<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import CartBadge from '@/components/CartBadge.vue';
import FilterSelect from '@/components/FilterSelect.vue';
import ActivityCarousel from '@/views/home/ActivityCarousel.vue';
import ProductCard from '@/views/product/ProductCard.vue';
import ProductCarousel from '@/views/product/ProductCarousel.vue';
import SearchBox from '@/views/search/SearchBox.vue';
import HotWords from '@/views/search/HotWords.vue';
import { enumLabel, useI18n } from '@/i18n';
import { fetchProducts, type ProductQuery, type WebProduct } from '@/lib/products';
import {
  fetchBrands,
  fetchCategories,
  fetchStores,
  type WebBrand,
  type WebCategory,
  type WebStore,
} from '@/lib/search';

const PAGE_SIZE = 12;
/** 轮播下面那两块各摆几行：五行的卡片高度和环那一圈差不多，再多就把商品挤到第二屏去了 */
const SHELF_SIZE = 5;
/** 筛选栏候选一次取满：门店/品牌复用搜索那两条分页接口，候选不能被分页切成半截 */
const OPTIONS_SIZE = 100;

/**
 * /?categoryId= 的落点：活动详情页里「跳转类型=分类」那一颗跳过来就带这一格。
 * 只当 categoryId 的初值用，不 watch —— props 参与 astro 的 island id，值一变 ClientRouter 就重挂整块，
 * 那一趟本来就重新走 onMounted，再 watch 一次就是同一次进站发两回商品请求。
 */
/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
const props = defineProps<{ categoryId?: number; locale?: string }>();

const { d, fill, href } = useI18n();

/** 环那一块：只取 shop_product.carousel_enable = 1 的（这一格在商品本表上，是整件商品的事，不是某个规格的） */
const carousel = ref<WebProduct[]>([]);
/** 下面的网格：全量上架商品（可按分类/品牌/门店筛），滚动加载。轮播位没商品时也不能让首页空着 */
const items = ref<WebProduct[]>([]);
const pageNum = ref(1);
const pageCount = ref(0);
const total = ref(0);

/** 筛选栏那三档。0 = 不限：原生 select 的候选值一律是字符串，拿 0 当哨兵比拿空串再到处转换省事 */
const categories = ref<WebCategory[]>([]);
const categoryId = ref(props.categoryId ?? 0);
const brandId = ref(0);
const storeId = ref(0);

/** 轮播下面并排的两块（只给启用中的，排序跟后台列表一致） */
const stores = ref<WebStore[]>([]);
const brands = ref<WebBrand[]>([]);

const loading = ref(true);
const loadError = ref('');
/** 换筛选条件后重查第 1 页只管「全部商品」那一块：商品接口没通不能把轮播和门店/品牌两块一起换成红框 */
const listLoading = ref(false);
const listError = ref('');
/** 后面某一页没 Load 出来另说一行，它和第 1 页共用 listLoading（同一时刻只有一趟列表请求在路上） */
const moreError = ref('');
/** 分类候选这一条单独一个错：下拉框空着不算商品没 Load 出来，别串到 listError 那一行去 */
const categoryError = ref('');
/** 这两块单独一条错：门店/品牌接口没通不能把整页商品换成红框 */
const shelfError = ref('');
/** 商品和这两块是两条 promise，商品先回来时这两块可能还在路上，得说「加载中」而不是「暂无」 */
const shelfLoading = ref(true);

const sentinel = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | undefined;

const hasMore = computed(() => pageNum.value < pageCount.value);

const filtered = computed(() => categoryId.value > 0 || brandId.value > 0 || storeId.value > 0);

/** 三档筛选条件拼成商品列表的入参。0 = 不限，那一档干脆不进对象（exactOptionalPropertyTypes 不许显式塞 undefined，axios 也就不带这个参数） */
const scope = computed<ProductQuery>(() => {
  const out: ProductQuery = {};
  if (categoryId.value > 0) out.categoryId = categoryId.value;
  if (brandId.value > 0) out.brandId = brandId.value;
  if (storeId.value > 0) out.storeId = storeId.value;
  return out;
});

/**
 * 分类下拉的项：服务端已经按「父在前、自己的下级紧跟其后」铺平，这里只补缩进。
 * 用不间断空格 —— 普通空格在 HTML 里会被折叠成一个，缩进就没了。
 */
const categoryOptions = computed(() =>
  categories.value.map((one) => ({
    id: one.id,
    label: `${'\u00A0\u00A0'.repeat(Math.max(0, (one.level ?? 1) - 1))}${one.categoryName}`,
  })),
);

/** \u54C1\u724C\u3001\u95E8\u5E97\u4E24\u6863\u540C\u5F62\uFF08\u5019\u9009\u5C31\u662F\u90A3\u4E24\u5757\u5217\u8868\uFF0C\u4E0D\u518D\u5207\u4E00\u5200\uFF09 */
const brandOptions = computed(() => brands.value.map((one) => ({ id: one.id, label: one.brandName })));
const storeOptions = computed(() => stores.value.map((one) => ({ id: one.id, label: one.storeName })));

interface ShelfRow {
  id: number;
  logo: string | null;
  name: string;
  /** 有这一格名字就挂成链接（门店、品牌都有详情页了） */
  url?: string;
  /** 门店挂营业状态那一枚小徽标；品牌没有这一档，留空就不渲染 */
  badge?: string;
  badgeOn?: boolean;
  sub: string;
}

interface Shelf {
  key: string;
  label: string;
  /** 「全部门店」/「暂无门店」这两句整句进字典：英文的语序和冠词跟中文不一样，不能拿 label 现拼半截 */
  allLabel: string;
  emptyLabel: string;
  moreUrl: string;
  rows: ShelfRow[];
}

/**
 * 两块的形状是一样的（图 + 名 + 一行次要信息），所以在这里归一成同一种行，模板只写一份行控件，
 * 也顺带保证并排时两栏每行等高、不会一边高一边矮。
 * 「全部」跳到搜索页对应的那个 tab：那一页不填关键字就是浏览全部，正好接得住这一格。
 */
const shelves = computed<Shelf[]>(() => [
  {
    key: 'store',
    label: d.enum.store,
    allLabel: d.home.allStores,
    emptyLabel: d.home.noStores,
    moreUrl: href('/search?tab=store'),
    rows: stores.value.slice(0, SHELF_SIZE).map((store) => ({
      id: store.id,
      logo: store.logo,
      name: store.storeName,
      url: href(`/store/${store.id}`),
      badge:
        store.businessStatus == null
          ? undefined
          : enumLabel(d.enum.businessStatus, store.businessStatus, fill(d.enum.unknown, { n: store.businessStatus })),
      badgeOn: store.businessStatus === 1,
      sub: [store.areaZh, store.address].filter(Boolean).join(' ') || d.home.noAddress,
    })),
  },
  {
    key: 'brand',
    label: d.enum.brand,
    allLabel: d.home.allBrands,
    emptyLabel: d.home.noBrands,
    moreUrl: href('/search?tab=brand'),
    rows: brands.value.slice(0, SHELF_SIZE).map((brand) => ({
      id: brand.id,
      logo: brand.logo,
      name: brand.brandName,
      url: href(`/brand/${brand.id}`),
      sub: brand.description || d.home.noBrandIntro,
    })),
  },
]);

/** 这两块不挡商品那一屏：单独一条 promise 走自己的错，接口没通只红下面那一行 */
async function loadShelves() {
  // 分类候选另走一条：它没通只是下拉框少一档可筛（后端还没上这条接口时就是这样），不能连累门店/品牌两块
  void fetchCategories().then(
    (list) => {
      categories.value = list;
    },
    (err: Error) => {
      categoryError.value = err.message;
    },
  );
  try {
    const [storePage, brandPage] = await Promise.all([
      fetchStores({ pageSize: OPTIONS_SIZE }),
      fetchBrands({ pageSize: OPTIONS_SIZE }),
    ]);
    stores.value = storePage.records;
    brands.value = brandPage.records;
  } catch (err) {
    shelfError.value = (err as Error).message;
  } finally {
    shelfLoading.value = false;
  }
}

async function loadPage(page: number) {
  const data = await fetchProducts({ pageSize: PAGE_SIZE, pageNum: page, ...scope.value });
  if (page === 1) {
    items.value = data.records;
  } else {
    // 两页之间有人上/下架的话 MySQL 不保证同一个快照，同一条可能被捞两次 ⇒ 按 id 去重再拼
    const seen = new Set(items.value.map((item) => item.id));
    items.value = [...items.value, ...data.records.filter((item) => !seen.has(item.id))];
  }
  pageCount.value = data.pages;
  pageNum.value = page;
  total.value = data.total;
}

async function loadMore() {
  if (listLoading.value || !hasMore.value) return;
  listLoading.value = true;
  moreError.value = '';
  try {
    await loadPage(pageNum.value + 1);
  } catch (err) {
    // 后面某一页没 Load 出来只说在下面那一行，别把已经显示出来的商品整块换成报错
    moreError.value = (err as Error).message;
  } finally {
    listLoading.value = false;
  }
}

/**
 * 换筛选条件就回第 1 页重查。
 *
 * 三颗下拉和那颗「清空筛选」在请求期间禁用（模板上 :disabled），所以不会有两条列表请求在路上抢着画结果，省下一套对号逻辑；
 * 清空那颗不按住的话也是同一个洞：这一条开头的 listLoading 闸门会把那一趟重查挡回去，旧那一档的结果回来照样画在「全部」那套筛选底下。
 * 第 1 页失败时已经显示出来的商品留着，只在筛选栏下面红一行 —— 整块换成空白更难认。
 */
async function refilter() {
  if (listLoading.value) return;
  listLoading.value = true;
  listError.value = '';
  moreError.value = '';
  try {
    await loadPage(1);
  } catch (err) {
    listError.value = (err as Error).message;
  } finally {
    listLoading.value = false;
  }
  await nextTick();
  armObserver();
}

/** 清空只做「改那三格」这一件事，重查交给下面那个 watch（三格一起改，watch 也只跑一趟） */
function clearFilters() {
  categoryId.value = 0;
  brandId.value = 0;
  storeId.value = 0;
}

watch([categoryId, brandId, storeId], refilter);

/**
 * 活动轮播（就在这一块上面）里那条「跳转类型=分类」的：把筛选栏的分类那一档选上。
 * 抛事件而不是拼一个 `/?categoryId=` 的链接：这两块在同一块 island 里，走 URL 还得处理
 * 「人已经在首页了再点一次」—— 同一路径 ClientRouter 不会重新挂载组件，那一趟 onMounted 里读参数根本不跑。
 * （活动详情页那颗「看这个分类的商品」是另一回事：那是从别的路径进首页，island 会重挂，所以那一颗走 URL。）
 *
 * 顺带把这一块的顶边滚到视口上沿：不滚的话点完只看见活动换了下一张，下面已经筛出来了没瞧见。
 */
const listSection = ref<HTMLElement | null>(null);

function pickCategory(id: number) {
  categoryId.value = id;
  listSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function armObserver() {
  if (!observer || !sentinel.value) return;
  // 网格换内容时 sentinel 那颗是重建过的新节点，观察者还盯着已经离开的旧节点，所以每回重画都要重新挂
  observer.disconnect();
  observer.observe(sentinel.value);
}

// 只在浏览器里取：onMounted 不在服务端跑，否则 SSR 和 hydration 会各发一次请求
onMounted(async () => {
  // 这两块不等商品：发出去就不管了，谁先回来谁先画（商品那一屏不该被门店接口拖住）
  void loadShelves();
  try {
    const [carouselPage] = await Promise.all([
      fetchProducts({ carouselEnable: 1, pageSize: PAGE_SIZE }),
      loadPage(1),
    ]);
    carousel.value = carouselPage.records;
  } catch (err) {
    loadError.value = (err as Error).message;
  } finally {
    loading.value = false;
  }

  // 触底那一格在 v-else 里，得等这一轮 DOM 更新完才拿得到
  await nextTick();
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadMore();
    },
    // 提前 200px 就开取下一页，省得人滑到底干等
    { rootMargin: '200px' },
  );
  armObserver();

  // 带 categoryId 进来的（活动详情页那颗「看这个分类的商品」）把这一块顶到视口上沿，和首页轮播那条 pickCategory 同一个落点。
  // 不给 behavior: 'smooth'：那是长距离动画，刚进一页就被推着往下滑，看着像页面自己跑掉了
  if (props.categoryId) listSection.value?.scrollIntoView({ block: 'start' });
});

onUnmounted(() => {
  observer?.disconnect();
});
</script>

<template>
  <section>
    <div class="mb-6 flex justify-end">
      <CartBadge />
    </div>

    <!-- 搜索框在轮播上面：进首页第一屏就够得着。限一个最大宽度居中，别拉成整行那么长 -->
    <!-- 框和下面那排热搜套在同一个限宽容器里：宽度是一件事，间距跟着这一排走（热搜没词时那一块不出现，间距就跟着收掉） -->
    <div class="mx-auto mb-8 max-w-2xl">
      <SearchBox />
      <HotWords class="mt-3" />
    </div>

    <p v-if="loading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ d.common.loading }}
    </p>

    <p v-else-if="loadError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fill(d.home.productsFetchFailed, { msg: loadError }) }}
    </p>

    <!-- 「一条商品都没有」不在这一层判：筛完没命中是常事，那样还得留着轮播和门店/品牌两块，话由下面那一块自己说 -->
    <template v-else>
      <!-- 轮播位一条商品都没有时整块不出现，别留一个转不动的空环 -->
      <section v-if="carousel.length" class="mb-10">
        <h2 class="mb-4 text-xl font-semibold text-gray-900 dark:text-gray-100">{{ d.home.carouselTitle }}</h2>
        <ProductCarousel :products="carousel" />
      </section>

      <!-- 轮播下面并排两块：门店、品牌。窄屏没地方并排，就上下摞 -->
      <section class="mb-10">
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div
            v-for="shelf in shelves"
            :key="shelf.key"
            class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <div class="mb-3 flex items-center justify-between">
              <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ shelf.label }}</h2>
              <a
                :href="shelf.moreUrl"
                class="text-sm text-blue-600 hover:underline dark:text-blue-400"
              >
                {{ shelf.allLabel }} ›
              </a>
            </div>

            <p v-if="shelfError" class="py-6 text-center text-sm text-red-600 dark:text-red-400">{{ shelfError }}</p>
            <p
              v-else-if="shelfLoading"
              class="py-6 text-center text-sm text-gray-500 dark:text-gray-400"
            >
              {{ d.common.loading }}
            </p>
            <p
              v-else-if="!shelf.rows.length"
              class="py-6 text-center text-sm text-gray-500 dark:text-gray-400"
            >
              {{ shelf.emptyLabel }}
            </p>

            <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
              <!-- 整行就是链接（他 2026-10-04 定的：点这一行任意一处都跳）。行里没有按钮和输入框，
                   包成 <a> 不会套掉别的东西；用真链接而不是 @click 是为了留住悬停预取、Ctrl/右键新开。
                   行没有详情页时才退化成 div（门店、品牌这一版都有 url，这一支是兜底） -->
              <li v-for="row in shelf.rows" :key="row.id" class="py-2">
                <component
                  :is="row.url ? 'a' : 'div'"
                  :href="row.url"
                  class="group flex cursor-pointer items-center gap-3"
                >
                  <img
                    v-if="row.logo"
                    :src="row.logo"
                    :alt="row.name"
                    class="h-10 w-10 shrink-0 rounded object-cover"
                    loading="lazy"
                  />
                  <span
                    v-else
                    class="flex h-10 w-10 shrink-0 items-center justify-center rounded bg-gray-100 text-base font-semibold text-gray-400 dark:bg-gray-700"
                  >
                    {{ row.name.charAt(0) }}
                  </span>

                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-2">
                      <span class="truncate text-sm font-medium text-gray-900 group-hover:underline dark:text-gray-100" :title="row.name">
                        {{ row.name }}
                      </span>
                      <span
                        v-if="row.badge"
                        class="shrink-0 rounded px-1.5 py-0.5 text-xs"
                        :class="
                          row.badgeOn
                            ? 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300'
                            : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
                        "
                      >
                        {{ row.badge }}
                      </span>
                    </div>
                    <p class="truncate text-xs text-gray-500 dark:text-gray-400" :title="row.sub">{{ row.sub }}</p>
                  </div>
                </component>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <!-- 活动轮播钉在「全部商品」上面：后台活动管理里那些「首页轮播位 + 投放中 + 配了图」的，谁也没配就整块不出现 -->
      <ActivityCarousel @select-category="pickCategory" />

      <section ref="listSection">
        <!-- 筛选栏钉在它要换的那块网格上面（跟后台列表那排 toolbar 同一个位置）。
             三档用同一颗可输入的下控（components/FilterSelect.vue）：候选是上百条量级，
             原生 select 既不能打字过滤，宽度还按当前那一项收缩、名字一长就和下拉箭头挤在一块 -->
        <div class="mb-4 flex flex-wrap items-end justify-between gap-3">
          <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
            {{ d.home.allProducts }}
            <span v-if="filtered" class="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">
              <template v-if="listLoading">{{ d.common.loading }}</template>
              <template v-else>{{ fill(d.common.total, { n: total, unit: d.home.unitItems }) }}</template>
            </span>
          </h2>

          <div class="flex flex-wrap items-center gap-2">
            <FilterSelect v-model="categoryId" :options="categoryOptions" :label="d.home.category" :disabled="listLoading" />
            <FilterSelect v-model="brandId" :options="brandOptions" :label="d.enum.brand" :disabled="listLoading" />
            <FilterSelect v-model="storeId" :options="storeOptions" :label="d.enum.store" :disabled="listLoading" />
            <button
              v-if="filtered"
              type="button"
              :disabled="listLoading"
              class="text-sm text-blue-600 hover:underline disabled:opacity-60 dark:text-blue-400"
              @click="clearFilters"
            >
              {{ d.home.clearFilters }}
            </button>
          </div>
        </div>

        <p v-if="categoryError" class="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
          {{ fill(d.home.categoryOptionsFailed, { msg: categoryError }) }}
        </p>
        <p v-else-if="listError" class="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
          {{ fill(d.home.listFetchFailed, { msg: listError }) }}
          <button type="button" class="ml-2 text-blue-600 hover:underline dark:text-blue-400" @click="refilter">
            {{ d.home.retry }}
          </button>
        </p>

        <div v-if="items.length" class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <!-- 每格钉死高度：卡片里的图是 flex-1，不钉高度就是一排高矮不齐。
               放大挂在这一格（不挂进 ProductCard 里）：轮播那张卡片的放大是 JS 按下标给的，共用一个类会打架。
               z-10 是必须的：gap-6 只有 24px，放大 1.08 就压到邻格上，不抬起来会被后面那些格子盖住半截 -->
          <div
            v-for="item in items"
            :key="item.id"
            class="relative h-[420px] transition-transform duration-300 hover:z-10 hover:scale-[1.08]"
          >
            <ProductCard :product="item" />
          </div>
        </div>
        <!-- 筛完没命中是常事，话要说清楚是「这个条件下没有」还是「本来就一条上架的都没有」 -->
        <p v-else class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ filtered ? d.home.nothingInScope : d.home.nothingOnSale }}
        </p>

        <!-- IntersectionObserver 盯的就是这一格：它进视口就续下一页 -->
        <div ref="sentinel" class="h-px"></div>
        <p v-if="items.length" class="mt-4 py-2 text-center text-sm text-gray-500 dark:text-gray-400">
          <template v-if="listLoading">{{ d.common.loading }}</template>
          <template v-else-if="moreError">
            {{ moreError }}
            <button type="button" class="ml-2 text-blue-600 hover:underline dark:text-blue-400" @click="loadMore">
              {{ d.home.retry }}
            </button>
          </template>
          <template v-else-if="hasMore">{{ d.home.scrollForMore }}</template>
          <template v-else>{{ d.home.noMoreProducts }}</template>
        </p>
      </section>
    </template>
  </section>
</template>
