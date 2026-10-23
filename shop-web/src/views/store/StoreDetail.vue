<script setup lang="ts">
/**
 * 门店详情页：头部（名称 + 分类那一档 + 营业状态 + 地址电话）→ 整宽轮播 → 底下那一排三档 tab（门店商品 / 文创商品 切档重拉第一页，门店评价换评论区）。
 *
 * 形状照 ProductDetail.vue：props 只收一个 storeId（路由那一趟从 Astro.params 读好传进来，岛不碰 window），
 * 取数全在 onMounted，「门店不存在/已停用」由后端那一句话直接当提示用，不另分 notFound 档。
 * 商品那一块另开一条错：本店没货和接口没通都只红下面那一块，不能把已经拿到的门店头部整页撤走。
 */
import { computed, onMounted, ref } from 'vue';
import { FwbButton } from 'flowbite-vue';
import CartBadge from '@/components/CartBadge.vue';
import CommentSection from '@/components/CommentSection.vue';
import FavoriteButton from '@/components/FavoriteButton.vue';
import ProductCard from '@/views/product/ProductCard.vue';
import StoreCarousel from '@/views/store/StoreCarousel.vue';
import { fetchProducts, parseImageUrls, type WebProduct } from '@/lib/products';
import { fetchCommentTotal } from '@/lib/comment';
import { fetchStoreDetail, type WebStore } from '@/lib/search';
import { enumLabel, useI18n } from '@/i18n';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
const props = defineProps<{ storeId: number; locale?: string }>();
const { d, fill, href } = useI18n();

const PAGE_SIZE = 12;

const store = ref<WebStore | null>(null);
const loading = ref(true);
const loadError = ref('');

const products = ref<WebProduct[]>([]);
const pageNum = ref(1);
const pageCount = ref(0);
/** 接口回的本店总条数（去重之前的数），只用来印「共 N 件」 */
const total = ref(0);
const productsLoading = ref(true);
const productsError = ref('');
const moreLoading = ref(false);
const moreError = ref('');

// 商品区两档：门店商品（cultural_enable=0）/ 文创商品（=1）。文创是这家店自己的周边，
// 后台录商品时打开「文创商品」那一档才会进这里，判据上它必然挂着 storeId，所以两档都照样带 storeId 查。
const PRODUCT_TABS = [
  { key: 'store', label: d.store.storeProducts, culturalEnable: 0 },
  { key: 'cultural', label: d.store.culturalProducts, culturalEnable: 1 },
] as const;
type ProductTabKey = (typeof PRODUCT_TABS)[number]['key'];
const productTab = ref<ProductTabKey>('store');
/** 评论那一档和两档商品并排在这一排里，但它不是一种 cultural_enable，所以单开一个开关而不是塞进 productTab */
const showComment = ref(false);
/** 评论那一档标签上印的条数：还没回来（或捞失败）留 null，那一格整格不出现，别闪一个 (0) 在那里 */
const commentCount = ref<number | null>(null);
const tabClass = (on: boolean) =>
  on
    ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
    : 'border-transparent text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200';
const culturalEnable = computed(() => (productTab.value === 'cultural' ? 1 : 0));
const emptyText = computed(() =>
  productTab.value === 'cultural' ? d.store.noCulturalProducts : d.store.noProducts
);

/**
 * 切换动画照商品详情页那一档：往后切面板从右边移进来、往前切从左边（CSS 在 global.css 最后那几条）。
 * 底下这一排是三档，所以把「两档商品 + 评论」合成一条序列来认方向；sliding 只在那段过渡里挂，
 * 用来给外面那一层临时加 overflow-x-clip（28px 的位移会探出容器，不裁就闪一条横向滚动条）。
 */
const PANEL_ORDER = ['store', 'cultural', 'comment'] as const;
type PanelKey = (typeof PANEL_ORDER)[number];
const panelKey = computed<PanelKey>(() => (showComment.value ? 'comment' : productTab.value));
const panelLabel = computed(() =>
  showComment.value ? d.store.comments : (PRODUCT_TABS.find((tab) => tab.key === productTab.value)?.label ?? ''),
);
const slideName = ref('tab-next');
const sliding = ref(false);

/** 方向得在改状态之前认：改完就不知道原来站在哪一档了 */
function setPanel(next: PanelKey) {
  if (next === panelKey.value) return;
  slideName.value = PANEL_ORDER.indexOf(next) > PANEL_ORDER.indexOf(panelKey.value) ? 'tab-next' : 'tab-prev';
}

const images = computed(() => parseImageUrls(store.value?.coverImages ?? null));
const hasMore = computed(() => pageNum.value < pageCount.value);

/** 头部那几枚小徽标：分类是这一页的第一格信息，所以和营业状态并排但配色分开 */
const storeTypeText = computed(() => {
  const type = store.value?.storeType;
  return type == null ? '' : d.enum.storeType[type] ?? '';
});
const businessStatusText = computed(() => {
  const status = store.value?.businessStatus;
  return enumLabel(d.enum.businessStatus, status, '');
});

/** 地址/电话/营业时间：空的整行不出现，别留一行只有标签没有值 */
const metaRows = computed(() => {
  const storeValue = store.value;
  if (!storeValue) return [];
  return [
    { key: 'address', label: d.store.address, value: [storeValue.areaZh, storeValue.address].filter(Boolean).join(' ') },
    { key: 'phone', label: d.store.phone, value: storeValue.contactPhone ?? '' },
    { key: 'hours', label: d.store.businessHours, value: storeValue.businessHours ?? '' },
  ].filter((row) => row.value);
});

async function loadProducts(page: number) {
  const data = await fetchProducts({
    storeId: props.storeId,
    culturalEnable: culturalEnable.value,
    pageSize: PAGE_SIZE,
    pageNum: page,
  });
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

/** 切档就从这一档的第一页重拉：两档的总数和续页进度都不一样，接着上一档的页码往下走会漏货 */
async function pickTab(key: ProductTabKey) {
  setPanel(key);
  showComment.value = false;
  if (productTab.value === key || productsLoading.value) return;
  productTab.value = key;
  products.value = [];
  productsError.value = '';
  moreError.value = '';
  productsLoading.value = true;
  await loadProductsFirst();
}

/** 评论这一档：只换下面那块，商品的翻页进度不动 */
function pickCommentPanel() {
  setPanel('comment');
  showComment.value = true;
}

/** 门店那一档标签上的条数单独捞一次（一页一条、只读 total）：tab 在评论区之前就得有数，等不到点开 */
async function loadCommentCount() {
  try {
    commentCount.value = await fetchCommentTotal(2, props.storeId);
  } catch {
    commentCount.value = null;
  }
}

onMounted(async () => {
  // 商品不等门店：这一条只吃 storeId，两趟并行发，谁先回来谁先画
  void loadProductsFirst();
  try {
    store.value = await fetchStoreDetail(props.storeId);
    // 门店拿到了才去数评论（带着错 id 不捞）；这一发不等它
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
      <a :href="href('/search?tab=store')" class="text-sm text-blue-600 hover:underline dark:text-blue-400">&larr; {{ d.store.backToList }}</a>
      <CartBadge />
    </div>

    <p v-if="loading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

    <!-- 停用/已删/没这个 id 后端回同一句话，这里直接把它当提示用 -->
    <div v-else-if="loadError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ loadError }}
    </div>

    <template v-else-if="store">
      <header class="mb-6">
        <div class="flex items-start gap-4">
          <img
            v-if="store.logo"
            :src="store.logo"
            :alt="store.storeName"
            class="h-16 w-16 shrink-0 rounded-lg border border-gray-200 object-cover dark:border-gray-700"
          />
          <span
            v-else
            class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-gray-100 text-2xl font-semibold text-gray-400 dark:bg-gray-700"
          >
            {{ store.storeName.charAt(0) }}
          </span>

          <div class="min-w-0 flex-1">
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ store.storeName }}</h1>

            <!-- 门店的分类（字典 store_type）打在名称下面第一排；营业状态另用一色，休息中的店也要能进来看 -->
            <div class="mt-2 flex flex-wrap items-center gap-2 text-xs">
              <span
                v-if="storeTypeText"
                class="rounded px-2 py-0.5 font-medium bg-blue-50 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300"
              >
                {{ storeTypeText }}
              </span>
              <span
                v-if="businessStatusText"
                class="rounded px-2 py-0.5 font-medium"
                :class="
                  store.businessStatus === 1
                    ? 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300'
                    : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
                "
              >
                {{ businessStatusText }}
              </span>
              <span
                v-if="store.isDelivery === 1"
                class="rounded px-2 py-0.5 font-medium bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300"
              >
                {{ d.store.delivery }}
              </span>
            </div>

            <ul v-if="metaRows.length" class="mt-3 space-y-1 text-sm text-gray-600 dark:text-gray-300">
              <li v-for="row in metaRows" :key="row.key">
                <span class="text-gray-400 dark:text-gray-500">{{ row.label }}</span>{{ row.value }}
              </li>
            </ul>
          </div>
        </div>
      </header>

      <StoreCarousel :images="images" :name="store.storeName" />

      <section class="mt-8">
        <div role="tablist" :aria-label="d.store.productsAria" class="mb-4 flex gap-6 border-b border-gray-200 dark:border-gray-700">
          <button
            v-for="tab in PRODUCT_TABS"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="!showComment && productTab === tab.key"
            class="-mb-px border-b-2 pb-2 text-sm font-medium transition-colors"
            :class="tabClass(!showComment && productTab === tab.key)"
            @click="pickTab(tab.key)"
          >
            {{ tab.label }}
          </button>
          <!-- 门店评论这一档：形状和商品那两档一样，点它只换下面那块，不碰商品的翻页进度 -->
          <button
            type="button"
            role="tab"
            :aria-selected="showComment"
            class="-mb-px border-b-2 pb-2 text-sm font-medium transition-colors"
            :class="tabClass(showComment)"
            @click="pickCommentPanel()"
          >
            {{ d.store.comments }}<span v-if="commentCount !== null" class="ml-1 text-gray-400 dark:text-gray-500">({{ commentCount }})</span>
          </button>
        </div>

        <!-- 跟着这一排认出来的那一档换 key ⇒ Vue 认成换了一个元素，<Transition> 就把旧那块左右移走完事再挂新的（方向见 slideName，CSS 在 global.css 最后那几条） -->
        <div :class="{ 'overflow-x-clip': sliding }">
          <Transition :name="slideName" mode="out-in" @before-leave="sliding = true" @after-enter="sliding = false">
            <div :key="panelKey" role="tabpanel" :aria-label="panelLabel">
              <!-- 门店不存在/停用那一趟已经整块红了，这一页不会带着错 id 去捞评论 -->
              <CommentSection v-if="showComment" preview :comment-type="2" :business-id="storeId" />

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
                  {{ emptyText }}
                </p>

                <template v-else>
                  <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
                    <!-- 每格钉死高度：卡片里的图是 flex-1，不钉就是一排高矮不齐（和首页那一排同一条理由） -->
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
                    <span class="text-sm text-gray-500 dark:text-gray-400">{{ fill(d.common.total, { n: total, unit: d.store.countUnit }) }}</span>
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

    <!-- 收藏那颗钉在根这一层：只吃 storeId，页面正在加载还是加载失败都不影响它点得动 -->
    <FavoriteButton :target-type="2" :target-id="storeId" :name="store?.storeName" />
  </div>
</template>
