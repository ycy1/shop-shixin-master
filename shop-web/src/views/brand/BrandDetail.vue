<script setup lang="ts">
/**
 * 品牌详情页：头部（LOGO + 名称 + 介绍）→ 品牌门店 → 品牌代言（图墙）→ 底下那一排两档 tab（品牌商品 / 品牌评价）。
 *
 * 形状照 StoreDetail.vue：props 只收一个 brandId（路由那一趟从 Astro.params 读好传进来，岛不碰 window），
 * 取数全在 onMounted，「品牌不存在/已停用」由后端那一句话直接当提示用，不另分 notFound 档。
 * 底下门店和商品两块各一条错：哪一块没通只红它自己那一块，不能把已经拿到的品牌头部整页撤走。
 */
import { computed, onMounted, ref } from 'vue';
import { FwbButton } from 'flowbite-vue';
import CartBadge from '@/components/CartBadge.vue';
import CommentSection from '@/components/CommentSection.vue';
import FavoriteButton from '@/components/FavoriteButton.vue';
import ImagePreview from '@/components/ImagePreview.vue';
import TabDropper from '@/components/TabDropper.vue';
import ProductCard from '@/views/product/ProductCard.vue';
import { fetchProducts, type WebProduct } from '@/lib/products';
import {
  fetchBrandDetail,
  fetchBrandStores,
  parseSpokespersons,
  type WebBrand,
  type WebBrandSpokesperson,
  type WebStore,
} from '@/lib/search';
import { fetchCommentTotal } from '@/lib/comment';
import { enumLabel, useI18n } from '@/i18n';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
const props = defineProps<{ brandId: number; locale?: string }>();
const { d, fill, href } = useI18n();

const PAGE_SIZE = 12;

const brand = ref<WebBrand | null>(null);
const loading = ref(true);
const loadError = ref('');

const stores = ref<WebStore[]>([]);
const storesLoading = ref(true);
const storesError = ref('');

const products = ref<WebProduct[]>([]);
const pageNum = ref(1);
const pageCount = ref(0);
/** 接口回的这个品牌名下商品总条数（去重之前的数），只用来印「共 N 件」 */
const total = ref(0);
const productsLoading = ref(true);
const productsError = ref('');
const moreLoading = ref(false);
const moreError = ref('');

/** 点开的代言人原图：只有一层预览，所以收 URL 不收下标（同个人中心那三颗头像） */
const previewUrl = ref('');

/** 底下那一排两档：品牌商品 / 品牌评价。评论不是一种查询条件，所以单开一个开关，切过去也不碰商品的翻页进度 */
const showComment = ref(false);
/** 品牌评价那一档标签上印的条数：还没回来（或捞失败）留 null，那一格整格不出现，别闪一个 (0) 在那里 */
const commentCount = ref<number | null>(null);
/** 递给 TabDropper 的那两档：品牌评价那颗多带一个数，条数没回来就不给（原来这截是模板里手写拼的） */
const tabItems = computed(() => [
  { key: 'products', label: d.brand.products },
  { key: 'comment', label: d.brand.comments, count: commentCount.value },
]);

/** 切换动画照商品详情页那一档：往后切面板从右边移进来、往前切从左边（CSS 在 global.css 最后那几条）；sliding 只在那段过渡里挂 */
const slideName = ref('tab-next');
const sliding = ref(false);

/** 点这一排的哪一档就往哪个方向移，认方向得在改状态之前 */
function pickPanel(show: boolean) {
  if (showComment.value === show) return;
  slideName.value = show ? 'tab-next' : 'tab-prev';
  showComment.value = show;
}

const spokespersons = computed<WebBrandSpokesperson[]>(() =>
  parseSpokespersons(brand.value?.brandSpokesperson ?? null),
);
const hasMore = computed(() => pageNum.value < pageCount.value);

/** 门店那一块归一成行（图 + 名 + 营业状态 + 一行次要信息），模板里就不重复算那串拼接了 */
const storeRows = computed(() =>
  stores.value.map((store) => ({
    id: store.id,
    logo: store.logo,
    name: store.storeName,
    typeText: store.storeType == null ? '' : d.enum.storeType[store.storeType] ?? '',
    statusText: enumLabel(d.enum.businessStatus, store.businessStatus, ''),
    statusOn: store.businessStatus === 1,
    sub: [store.areaZh, store.address].filter(Boolean).join(' ') || d.brand.noAddress,
  })),
);

async function loadStores() {
  try {
    stores.value = await fetchBrandStores(props.brandId);
  } catch (err) {
    storesError.value = (err as Error).message;
  } finally {
    storesLoading.value = false;
  }
}

async function loadProducts(page: number) {
  const data = await fetchProducts({ brandId: props.brandId, pageSize: PAGE_SIZE, pageNum: page });
  if (page === 1) {
    products.value = data.records;
  } else {
    // 两页之间有人上/下架，同一条可能被捞两次 ⇒ 按 id 去重再拼（和首页续页同一处理）
    const seen = new Set(products.value.map((item) => item.id));
    products.value = [...products.value, ...data.records.filter((item) => !seen.has(item.id))];
  }
  pageCount.value = data.pages;
  pageNum.value = page;
  total.value = data.total;
}

async function loadProductsFirst() {
  try {
    await loadProducts(1);
  } catch (err) {
    productsError.value = (err as Error).message;
  } finally {
    productsLoading.value = false;
  }
}

async function loadMore() {
  if (moreLoading.value || !hasMore.value) return;
  moreLoading.value = true;
  moreError.value = '';
  try {
    await loadProducts(pageNum.value + 1);
  } catch (err) {
    // 续页失败只红下面那一行 + 一颗重试，别把已经画出来的商品撤走
    moreError.value = (err as Error).message;
  } finally {
    moreLoading.value = false;
  }
}

/** 品牌评价那一档标签上的条数单独捞一次（一页一条、只读 total）：tab 在评论区之前就得有数，等不到点开 */
async function loadCommentCount() {
  try {
    commentCount.value = await fetchCommentTotal(3, props.brandId);
  } catch {
    commentCount.value = null;
  }
}

onMounted(async () => {
  // 门店和商品不等品牌头部：这两条只吃 brandId，三趟并行发，谁先回来谁先画
  void loadStores();
  void loadProductsFirst();
  try {
    brand.value = await fetchBrandDetail(props.brandId);
    // 品牌拿到了才去数评论（带着错 id 不捞）；这一发不等它
    loadCommentCount();
  } catch (err) {
    loadError.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <a :href="href('/search?tab=brand')" class="text-sm text-blue-600 hover:underline dark:text-blue-400">&larr; {{ d.brand.backToList }}</a>
      <CartBadge />
    </div>

    <p v-if="loading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

    <!-- 停用/已删/没这个 id 后端回同一句话，这里直接把它当提示用 -->
    <div v-else-if="loadError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ loadError }}
    </div>

    <template v-else-if="brand">
      <header class="mb-8">
        <div class="flex items-start gap-4">
          <img
            v-if="brand.logo"
            :src="brand.logo"
            :alt="brand.brandName"
            class="h-16 w-16 shrink-0 rounded-lg border border-gray-200 bg-white object-contain p-1 dark:border-gray-700"
          />
          <span
            v-else
            class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-gray-100 text-2xl font-semibold text-gray-400 dark:bg-gray-700"
          >
            {{ brand.brandName.charAt(0) }}
          </span>

          <div class="min-w-0 flex-1">
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ brand.brandName }}</h1>
            <!-- 介绍是这一页唯一一段整块的字，所以留 whitespace-pre-line：后台那一格是textarea，有人手工换过行 -->
            <p
              v-if="brand.description"
              class="mt-2 whitespace-pre-line text-sm leading-6 text-gray-600 dark:text-gray-300"
            >
              {{ brand.description }}
            </p>
          </div>
        </div>
      </header>

      <section class="mb-8">
        <h2 class="mb-4 text-xl font-semibold text-gray-900 dark:text-gray-100">{{ d.brand.stores }}</h2>

        <p v-if="storesLoading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

        <div
          v-else-if="storesError"
          class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
        >
          {{ storesError }}
          <button type="button" class="ml-2 underline" @click="loadStores">{{ d.detailCommon.retry }}</button>
        </div>

        <p v-else-if="!stores.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ d.brand.noStores }}
        </p>

        <!-- 门店行：只有名称挂链接（同首页那一块和商品卡片的口径），整行包成 <a> 会把营业状态那枚徽标套进链接里 -->
        <ul v-else class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <li
            v-for="row in storeRows"
            :key="row.id"
            class="rounded-lg border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <!-- 整行都是链接（口径同首页门店栏、搜索页门店卡片）：行里没有按钮和输入框，包成 <a> 不抢东西 -->
            <a :href="href(`/store/${row.id}`)" class="group flex cursor-pointer items-center gap-3 p-3">
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
                  <span
                    class="truncate text-sm font-medium text-gray-900 group-hover:underline dark:text-gray-100"
                    :title="row.name"
                  >
                    {{ row.name }}
                  </span>
                  <span
                    v-if="row.statusText"
                    class="shrink-0 rounded px-1.5 py-0.5 text-xs"
                    :class="
                      row.statusOn
                        ? 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300'
                        : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
                    "
                  >
                    {{ row.statusText }}
                  </span>
                </div>
                <p class="truncate text-xs text-gray-500 dark:text-gray-400" :title="[row.typeText, row.sub].filter(Boolean).join(' · ')">
                  {{ [row.typeText, row.sub].filter(Boolean).join(' · ') }}
                </p>
              </div>
            </a>
          </li>
        </ul>
      </section>

      <!-- 品牌代言（后台那一格最多 6 张）：一张都没填就整块不出现，别留一个只有标题的空格。
           排列按他 2026-10-04 定的「自由拼接」：走 CSS 多列而不是 grid —— 每列自己往下接，
           图按自身比例出高度（不再钉 aspect-square 裁成方格），所以横图竖图能咬合住、不留空格。
           li 上那两格是这一条链的必要件：break-inside-avoid 让一张图不被劈到两列，mb-4 补上列内的竖向间距
           （多列的 gap 只管横向，竖向得自己留）。
           这一块的顺序也是他这一轮定的：头部 → 品牌门店 → 品牌代言 → 品牌商品 -->
      <section v-if="spokespersons.length" class="mb-8">
        <h2 class="mb-4 text-xl font-semibold text-gray-900 dark:text-gray-100">{{ d.brand.ambassadors }}</h2>
        <ul class="columns-2 gap-4 sm:columns-3">
          <li v-for="(one, index) in spokespersons" :key="`${one.url}-${index}`" class="mb-4 break-inside-avoid">
            <button
              type="button"
              class="group block w-full cursor-zoom-in text-left"
              :aria-label="fill(d.brand.zoomAria, { name: one.name || d.brand.spokespersonImage })"
              @click="previewUrl = one.url"
            >
              <img
                :src="one.url"
                :alt="one.name || d.brand.spokespersonImage"
                class="w-full rounded-lg border border-gray-200 transition-transform duration-300 group-hover:scale-[1.03] dark:border-gray-700"
                loading="lazy"
              />
              <p v-if="one.name" class="mt-1.5 truncate text-sm text-gray-600 group-hover:underline dark:text-gray-300" :title="one.name">
                {{ one.name }}
              </p>
            </button>
          </li>
        </ul>
      </section>

      <section>
        <!-- 等分那一排 + 水滴胶囊（原来落在选中那颗文字底下的 border-b-2 由胶囊担当，切换时拉长再收住） -->
        <TabDropper
          :label="d.brand.products"
          list-class="mb-4 gap-6"
          :model-value="showComment ? 'comment' : 'products'"
          :items="tabItems"
          @update:model-value="(key: string) => pickPanel(key === 'comment')"
        />

        <!-- 跟着当前那一档换 key ⇒ Vue 认成换了一个元素，<Transition> 就把旧那块左右移走完事再挂新的；
             外层这一格只在过渡那一段挂 overflow-x-clip，28px 的位移会探出容器，不裁就闪一条横向滚动条 -->
        <div :class="{ 'overflow-x-clip': sliding }">
          <Transition :name="slideName" mode="out-in" @before-leave="sliding = true" @after-enter="sliding = false">
            <div :key="showComment ? 'comment' : 'products'" role="tabpanel" :aria-label="showComment ? d.brand.comments : d.brand.products">
              <!-- 品牌不存在/停用那一趟已经整块红了，这一页不会带着错 id 去捞评论 -->
              <CommentSection v-if="showComment" preview :comment-type="3" :business-id="brandId" />

              <template v-else>
                <p v-if="productsLoading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

                <div
                  v-else-if="productsError"
                  class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
                >
                  {{ productsError }}
                  <button type="button" class="ml-2 underline" @click="loadProductsFirst">{{ d.detailCommon.retry }}</button>
                </div>

                <p v-else-if="!products.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
                  {{ d.brand.noProducts }}
                </p>

                <template v-else>
                  <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
                    <!-- 每格钉死高度：卡片里的图是 flex-1，不钉就是一排高矮不齐（和门店详情页那一排同一条理由） -->
                    <div
                      v-for="item in products"
                      :key="item.id"
                      class="relative h-[420px] transition-transform duration-300 hover:z-10 hover:scale-[1.08]"
                    >
                      <ProductCard :product="item" />
                    </div>
                  </div>

                  <p v-if="moreError" class="mt-4 text-center text-sm text-red-600 dark:text-red-400">
                    {{ moreError }}
                    <button type="button" class="ml-2 underline" @click="loadMore">{{ d.detailCommon.retry }}</button>
                  </p>

                  <div class="mt-4 flex items-center justify-center gap-3">
                    <span class="text-sm text-gray-500 dark:text-gray-400">{{ fill(d.common.total, { n: total, unit: d.brand.countUnit }) }}</span>
                    <FwbButton v-if="hasMore && !moreError" color="blue" size="sm" :disabled="moreLoading" @click="loadMore">
                      {{ moreLoading ? d.common.loading : fill(d.common.more, { n: PAGE_SIZE }) }}
                    </FwbButton>
                  </div>
                </template>
              </template>
            </div>
          </Transition>
        </div>
      </section>
    </template>

    <!-- 收藏那颗钉在根这一层：只吃 brandId，页面正在加载还是加载失败都不影响它点得动 -->
    <FavoriteButton :target-type="3" :target-id="brandId" :name="brand?.brandName" />

    <ImagePreview v-if="previewUrl" :src="previewUrl" :alt="d.brand.spokespersonImage" @close="previewUrl = ''" />
  </div>
</template>
