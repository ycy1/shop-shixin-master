<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { FwbButton } from 'flowbite-vue';
import CartBadge from '@/components/CartBadge.vue';
import CommentSection from '@/components/CommentSection.vue';
import FavoriteButton from '@/components/FavoriteButton.vue';
import { useAuthStore } from '@/stores/auth';
import { useCartStore } from '@/stores/cart';
import { fetchProductDetail, parseImageUrls, type WebProductDetail, type WebProductSku } from '@/lib/products';
import { fetchCommentTotal } from '@/lib/comment';
import { useI18n } from '@/i18n';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
const props = defineProps<{ productId: number; locale?: string }>();
const { d, fill, href } = useI18n();

const auth = useAuthStore();
const cart = useCartStore();

const product = ref<WebProductDetail | null>(null);
const loading = ref(true);
const loadError = ref('');

/**
 * 加购这一颗现在是落库的（库里那张购物车按人记，得先知道是谁），所以要登录态。
 * 没登录时按钮照画，只是挂上 data-open-login 让头部那颗登录窗认领这一颗点击 ——
 * 形状和 null/false 那一坑都同详情页右下角那颗收藏按钮（见 components/FavoriteButton.vue）。
 */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);

// 数量不封顶到库存之外：加购时要能挡住「下单数大于可用库存」
const quantity = ref(1);

/** 点过一次加购之后这三格要说清结果：处理中、错在哪（后端那句业务提示）、成了没（成功不弹框，就地印一行） */
const adding = ref(false);
const addError = ref('');
const added = ref(false);

/** 当前选中的那一格规格。整件商品的价/库存/赠送积分都是它的，没有它就是还没定下来 */
const selectedSkuId = ref<number | null>(null);

/** 评论那一档 tab 标签上要印的条数：还没回来（或捞失败）留 null，那一格整格不出现，别闪一个 (0) 在那里 */
const commentCount = ref<number | null>(null);

// 商品（SPU）自己的那组图：images 里的轮播，一条都没配就回落 mainImage；选中规格的图会排在它前面（见 shots）
const gallery = ref<string[]>([]);
const shot = ref(0);

/** 默认选中：第一条「有货」的规格，一条都没有就落第一条（让页面有个选中态，按钮自己说为什么点不动） */
function defaultSku(skus: WebProductSku[]): WebProductSku | undefined {
  return skus.find((sku) => (sku.availableStock ?? 0) > 0) ?? skus[0];
}

// 只在浏览器里取：onMounted 不在服务端跑，否则 SSR 和 hydration 会各发一次请求
onMounted(async () => {
  ready.value = true;
  try {
    const data = await fetchProductDetail(props.productId);
    product.value = data;
    gallery.value = [...parseImageUrls(data.images)];
    if (!gallery.value.length && data.mainImage) gallery.value = [data.mainImage];
    const preset = defaultSku(data.skus ?? []);
    selectedSkuId.value = preset?.id ?? null;
    // 一上来就落在 1 件；这一格预设的规格已经没货了就写 0（加购按钮本来就禁用着）
    quantity.value = stockOf(preset) > 0 ? 1 : 0;
    // 商品拿到了才去数评论（带着错 id 不捞）；这一发不等它，别把首屏压在多一次往返之后
    loadCommentCount();
  } catch (err) {
    loadError.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
});

/** tab 标签上那个数单独捞一次（一页一条、只读 total），等不到就算了，评论区点开照样有自己的取数 */
async function loadCommentCount() {
  try {
    commentCount.value = await fetchCommentTotal(1, props.productId);
  } catch {
    commentCount.value = null;
  }
}

const skus = computed(() => product.value?.skus ?? []);
const selectedSku = computed(() => skus.value.find((sku) => sku.id === selectedSkuId.value) ?? null);

function stockOf(sku: WebProductSku | null | undefined) {
  return sku?.availableStock ?? 0;
}
const stockCount = computed(() => stockOf(selectedSku.value));
/** 这一格是「能不能加进车」的总结论：没选中规格，或者它没货，都不给点（规格的 sale_enable 不参与，那是 ERP 建销售单的开关） */
const canAdd = computed(() => !!selectedSku.value && stockCount.value > 0);
const addButtonText = computed(() => {
  if (!signedIn.value) return d.product.loginToAddToCart;
  if (!selectedSku.value) return d.product.noSpecAvailable;
  if (stockCount.value <= 0) return d.product.outOfStock;
  return d.product.addToCart;
});

/**
 * 「立即购买」那条链接：直接把选中的规格和件数挂在 URL 上交给结算页，不落库、不动购物车。
 * 没货或还没选规格时给空串，模板那一颗就不画成链接。登录态不在这儿拦：结算页自己会说「请先登录」。
 */
const buyNowHref = computed(() => {
  const p = product.value;
  const sku = selectedSku.value;
  if (!p || !sku || quantity.value < 1 || stockCount.value <= 0) return '';
  return href(`/checkout?p=${p.id}&sku=${sku.id}&qty=${quantity.value}`);
});

/** 规格文本这格后台可空（无规格的默认SKU），页面自己补一个说法 */
function specLabel(sku: WebProductSku) {
  return sku.specText?.trim() || d.product.defaultSpec;
}

/**
 * 大图这一组：选中规格自己配的那几张排在头一组（换一档规格就整组换掉，游标跟着归零，这就是「跟着规格图切换」），
 * 商品自己的轮播跟在后面照样能翻；这一条规格没配图（sku_image 为 NULL 的语义就是沿用商品图）时整组就是商品的轮播/主图。
 */
const shots = computed<string[]>(() => {
  const sku = selectedSku.value;
  const specImages = parseImageUrls(sku?.skuImages ?? null);
  // 只在 sku_image 上留过一张的老数据，规格那一组就是那一张
  if (!specImages.length && sku?.skuImage) specImages.push(sku.skuImage);
  if (!specImages.length) return gallery.value;
  const shown = new Set(specImages);
  return [...specImages, ...gallery.value.filter((url) => !shown.has(url))];
});
const shownImage = computed(() => shots.value[shot.value] || '');

/**
 * 主图那颗放大镜：鼠标停在哪个点上，就以那一点为原点放大。
 * 原点存百分比而不是像素 —— 换一档规格、换一张图、窗口改宽窄都不用回来重算，
 * 探出去的那一圈由外层那层 overflow-hidden 裁掉（放大后内容必然盖满整格，不会露出空白）。
 */
const ZOOM = 2;
const zooming = ref(false);
const zoomOrigin = ref('50% 50%');

function trackLens(e: MouseEvent) {
  const box = (e.currentTarget as HTMLElement).getBoundingClientRect();
  // 图片还没量出尺寸（那一格被 v-if 摘掉、或宽度为 0）就别算，否则除出 NaN 写进 style 里
  if (!box.width || !box.height) return;
  const x = ((e.clientX - box.left) / box.width) * 100;
  const y = ((e.clientY - box.top) / box.height) * 100;
  zoomOrigin.value = `${x.toFixed(1)}% ${y.toFixed(1)}%`;
}

const priceText = computed(() => {
  const p = product.value;
  if (!p) return '0.00';
  return Number(selectedSku.value?.productPrice ?? p.priceMin ?? 0).toFixed(2);
});

// 只有一条规格时这一排就一格，摆出来反而像让人「选」一个没得选的东西
const showSpecPicker = computed(() => skus.value.length > 1);

// 这个数跟着选中的那一档规格走：销量记在每条规格上，商品（SPU）那一份不给汇总数
const soldCount = computed(() => selectedSku.value?.salesCount ?? 0);
const gainPoints = computed(() => selectedSku.value?.gainPoints ?? 0);

/**
 * 名称后面那几行次要信息：形状照门店详情页的 metaRows（标签 + 值），值另给一格 url ——
 * 只有品牌挂链接（品牌有详情页），而且只挂值、不挂「品牌：」那个标签。
 */
const metaRows = computed(() =>
  [
    {
      key: 'brand',
      label: d.product.brand,
      value: product.value?.brandName ?? '',
      url: product.value?.brandId ? href(`/brand/${product.value.brandId}`) : '',
    },
    { key: 'category', label: d.product.category, value: product.value?.categoryName ?? '', url: '' },
    { key: 'store', label: d.product.store, value: product.value?.storeName ?? '', url: '' },
    { key: 'code', label: d.product.code, value: product.value?.productCode ?? '', url: '' },
  ].filter((row) => row.value),
);

/**
 * 规格参数那几行：名称在左、值和单位在右。
 * 值和单位在后台都是可填可不填的（只有规格名必填），所以两个都空时右侧画一个「—」占着，不把这行删掉 ——
 * 「重量」这一格没人填值，用户也要看得见这个商品是有「重量」这一项的。
 */
const specRows = computed(() =>
  (product.value?.specs ?? []).map((item) => ({
    key: item.id,
    name: item.specName ?? '',
    value: [item.specValue, item.specUnit].filter(Boolean).join(' '),
  })),
);

/**
 * 下半区只剩这两块：2026-10-05 他把商品描述、商品规格合进了「商品详情」这一个 tab。
 * 合进来的三块在面板里按 描述 → 规格参数 → 图文 的顺序各占一节，哪一节没内容就整节不出现
 * （口径同品牌详情页那块代言人 —— 不留一个只有标题的空格）。
 */
type TabKey = 'detail' | 'comment';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'detail', label: d.product.tabDetail },
  { key: 'comment', label: d.product.tabComment },
];

const active = ref<TabKey>('detail');
/** 这次是往后切还是往前切（决定面板从哪边移进来）；sliding 只在那段过渡里挂，见 global.css 最后那一条 */
const slideName = ref('tab-next');
const sliding = ref(false);
const currentLabel = computed(() => TABS.find((item) => item.key === active.value)?.label ?? '');

function selectTab(key: TabKey) {
  if (active.value === key) return;
  const from = TABS.findIndex((item) => item.key === active.value);
  slideName.value = TABS.findIndex((item) => item.key === key) > from ? 'tab-next' : 'tab-prev';
  active.value = key;
}

/** 图文详情跟着当前选中的那一档规格走，和上面大图那一组是两码事（那一组会轮播，这一组从上往下铺） */
const detailShots = computed(() => parseImageUrls(selectedSku.value?.detailImages ?? null));

function selectSku(sku: WebProductSku) {
  if (selectedSkuId.value === sku.id) return;
  selectedSkuId.value = sku.id;
  // 换一档规格就把上一档那句加购结果收掉：那句话说的是刚才那一条，留着会以为这一档也加过了
  addError.value = '';
  added.value = false;
  // 换规格就把整组图的头一张换掉了，游标不归零会停在上一组的第 N 张上，大图就不是这一档的规格图
  shot.value = 0;
  // 换规格就是换库存：原来选了 5 件、新规格只剩 2 件的话把数量收回来，别留一个提交必失败的值
  const stock = stockOf(sku);
  if (quantity.value > stock) quantity.value = stock;
}

function step(delta: number) {
  const next = quantity.value + delta;
  // 缺货（库存 0）时这一格停在 0，加购按钮本来就禁用着
  if (next < 0 || next > stockCount.value) return;
  quantity.value = next;
}

async function addAll() {
  const p = product.value;
  const sku = selectedSku.value;
  // 没登录这一支走不到这里：那颗按钮外面那一层挂着 data-open-login，点击归头部的登录窗认领
  if (!p || !sku || quantity.value < 1 || !signedIn.value || adding.value) return;
  adding.value = true;
  addError.value = '';
  added.value = false;
  try {
    // 车上已经有这一条就在那一行上叠这几件（不是覆盖成这个数），所以重复点同一档规格是把 2 件买成 4 件
    await cart.add(sku.id, quantity.value);
    added.value = true;
  } catch (err) {
    // 后端那句业务提示（「已下架」「数量要大于 0」这类）比状态码准，原样印在按钮下面
    addError.value = (err as Error).message;
  } finally {
    adding.value = false;
  }
}
</script>

<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <a :href="href('/')" class="text-sm text-blue-600 hover:underline dark:text-blue-400">&larr; {{ d.product.backToList }}</a>
      <CartBadge />
    </div>

    <p v-if="loading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

    <!-- 下架/已删/没这个 id 后端回同一句话，这里直接把它当提示用 -->
    <div v-else-if="loadError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ loadError }}
    </div>

    <template v-else-if="product">
      <div class="grid grid-cols-1 gap-8 lg:grid-cols-2">
        <!-- 缩略图竖着排在主图左边（2026-10-05 他点名要的形状）：整排是 flex 的一列，主图那一格 min-w-0 才收得住，
             否则 aspect-square 会按没缩过的内容宽去量，把这一栏撑出格子 -->
        <div class="flex items-start gap-3">
          <!-- 缩略图与大图同一组图：以选中规格自己那几张打头（换一档规格游标归零就落在它的封面上），后面跟着商品自己的轮播 -->
          <div v-if="shots.length > 1" class="flex shrink-0 flex-col gap-2">
            <button
              v-for="(url, index) in shots"
              :key="url"
              type="button"
              class="h-16 w-16 overflow-hidden rounded border bg-white dark:bg-gray-800"
              :class="index === shot ? 'border-blue-500' : 'border-gray-200 dark:border-gray-700'"
              @click="shot = index"
            >
              <img :src="url" :alt="`${product.productName} ${index + 1}`" class="h-full w-full object-cover" />
            </button>
          </div>

          <div class="min-w-0 flex-1">
            <div
              v-if="shownImage"
              class="aspect-square w-full cursor-zoom-in overflow-hidden rounded-lg border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800"
              @mouseenter="zooming = true"
              @mousemove="trackLens"
              @mouseleave="zooming = false"
            >
              <!-- 移入期间不带 transition：鼠标一路走原点一路改，缓动会让放大中心拖着半拍跟不上手；
                   移出那一下才挂上 transition，收回去是有动画的（改样式的同一帧里 transition 已生效，收得住） -->
              <img
                :src="shownImage"
                :alt="product.productName"
                class="h-full w-full object-cover"
                :class="zooming ? '' : 'transition-transform duration-200 ease-out'"
                :style="{ transform: zooming ? `scale(${ZOOM})` : 'scale(1)', transformOrigin: zoomOrigin }"
              />
            </div>
            <div
              v-else
              class="flex aspect-square w-full items-center justify-center rounded-lg border border-gray-200 bg-gray-50 text-sm text-gray-400 dark:border-gray-700 dark:bg-gray-800"
            >
              {{ d.product.noImage }}
            </div>
          </div>
        </div>

        <div>
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ product.productName }}</h1>
          <p v-if="product.subTitle" class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ product.subTitle }}</p>

          <div class="mt-4 rounded-lg bg-gray-50 px-4 py-3 dark:bg-gray-800">
            <span class="text-2xl font-semibold text-red-600 dark:text-red-400">¥{{ priceText }}</span>
            <!-- 选定一格规格之后这个数才是这个数的口径：以前这里还摆「¥min ~ ¥max 区间」，现在区间只在列表卡片上有意义 -->
            <span v-if="selectedSku" class="ml-3 text-sm text-gray-500 dark:text-gray-400">
              {{ specLabel(selectedSku) }}
            </span>
          </div>

          <p class="mt-4 flex flex-wrap gap-x-4 text-sm text-gray-600 dark:text-gray-300">
            <span>{{ fill(d.product.sold, { n: soldCount }) }}</span>
            <span :class="stockCount > 0 ? '' : 'font-medium text-red-600 dark:text-red-400'">
              {{ stockCount > 0 ? fill(d.product.stock, { n: stockCount }) : d.product.outOfStock }}
            </span>
            <span v-if="gainPoints > 0">{{ fill(d.product.gainPoints, { n: gainPoints }) }}</span>
          </p>

          <ul class="mt-4 space-y-1 text-sm text-gray-600 dark:text-gray-300">
            <li v-for="row in metaRows" :key="row.key">
              <span class="text-gray-400 dark:text-gray-500">{{ row.label }}</span>
              <a v-if="row.url" :href="row.url" class="text-blue-600 hover:underline dark:text-blue-400">{{ row.value }}</a>
              <template v-else>{{ row.value }}</template>
            </li>
          </ul>

          <!-- 一排规格按钮：一件商品下面每一条规格各自一个价、一份库存，加购传的就是它的 id。
               只有一条规格时整排不出现（那就是「默认规格」，没得选也不用选）。
               每条都能选：哪一档没货由下面那两颗按钮自己说（步进和加购在 0 库存时本来就点不动） -->
          <div v-if="showSpecPicker" class="mt-5">
            <p class="mb-2 text-sm text-gray-500 dark:text-gray-400">{{ d.product.selectSpec }}</p>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="sku in skus"
                :key="sku.id"
                type="button"
                class="rounded-lg border px-3 py-1.5 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50"
                :class="
                  sku.id === selectedSkuId
                    ? 'border-blue-600 bg-blue-50 text-blue-700 dark:border-blue-500 dark:bg-blue-900/40 dark:text-blue-200'
                    : 'border-gray-200 text-gray-700 hover:border-gray-400 dark:border-gray-700 dark:text-gray-300 dark:hover:border-gray-500'
                "
                @click="selectSku(sku)"
              >
                {{ specLabel(sku) }}
              </button>
            </div>
          </div>

          <div class="mt-6 flex items-center gap-4">
            <!-- overflow-hidden 是必须的：两颗步进按钮自己没圆角，外层那圈 rounded-lg 只刷自己的边框、
                 不裁子元素，所以鼠标一移进去就是一块方的 hover 底色顶出圆角（看着像按钮变正方形了） -->
            <div
              class="flex items-center overflow-hidden rounded-lg border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800"
            >
              <button
                type="button"
                class="px-3 py-2 text-gray-500 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300 dark:text-gray-400 dark:hover:bg-gray-700 dark:disabled:text-gray-600"
                :disabled="quantity <= 1"
                @click="step(-1)"
              >
                −
              </button>
              <span class="w-12 text-center text-sm font-medium">{{ quantity }}</span>
              <button
                type="button"
                class="px-3 py-2 text-gray-500 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300 dark:text-gray-400 dark:hover:bg-gray-700 dark:disabled:text-gray-600"
                :disabled="quantity >= stockCount"
                @click="step(1)"
              >
                +
              </button>
            </div>
            <!-- 没登录时那颗按钮的 data-open-login 挂在这一层而不是 FwbButton 上：它的属性会不会透传到自带的
                 <button> 上没查产物，而头部那颗登录窗认的是 closest('[data-open-login]') —— 从按钮往上找，这一层一样算。
                 包一层 span 是为了只罩住这一颗粒：整排放属性的话，没登录的人点点数量步进也会被拉去登录窗。 -->
            <span :data-open-login="signedIn ? null : ''" class="inline-flex">
              <FwbButton color="blue" :disabled="!canAdd || adding" @click="addAll">
                {{ adding ? d.product.adding : addButtonText }}
              </FwbButton>
            </span>
            <!-- 立即购买：把选中的规格和件数交给结算页，一条商品一单，不落购物车。
                 没登录也给的是一条真链接 —— 结算页自己会说「请先登录」，在这儿再拦一次只是多一句废话 -->
            <a
              v-if="buyNowHref"
              :href="buyNowHref"
              class="rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600"
            >
              {{ d.product.buyNow }}
            </a>
          </div>

          <!-- 加购的结果就地印一句，不弹框：这一页还要让人接着挑（换规格、改件数再叠一件） -->
          <p v-if="addError" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ addError }}</p>
          <p v-else-if="added" class="mt-2 text-sm text-green-600 dark:text-green-400">
            {{ d.product.addedToCart }}
            <a :href="href('/cart')" class="underline hover:text-green-700 dark:hover:text-green-300">{{ d.product.goToCart }}</a>
          </p>
        </div>
      </div>

      <!-- 下半区两块：商品详情（描述 + 规格参数 + 图文都并在这一 tab 里）/ 评论。
           那排标签的形状照搜索页的三个 tab（同一条 border-b + 选中那根蓝色下划线），只把居中改成靠左 ——
           这一页上面是两栏内容，居中那排会孤零零悬在中间。 -->
      <div role="tablist" :aria-label="d.product.infoAria"
        class="mt-8 flex gap-6 border-b border-gray-200 dark:border-gray-700">
        <button
          v-for="item in TABS"
          :key="item.key"
          type="button"
          role="tab"
          :aria-selected="active === item.key"
          class="-mb-px border-b-2 pb-2 text-sm font-medium transition-colors"
          :class="
            active === item.key
              ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
              : 'border-transparent text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200'
          "
          @click="selectTab(item.key)"
        >
          {{ item.label }}<span v-if="item.key === 'comment' && commentCount !== null" class="ml-1 text-gray-400 dark:text-gray-500">({{ commentCount }})</span>
        </button>
      </div>

      <!-- 跟着 active 换 key ⇒ Vue 认成换了一个元素，<Transition> 就把旧那块左右移走完事再挂新的（方向见 slideName，CSS 在 global.css 最后那一条）。
           外层这一格只在过渡那一段挂 overflow-x-clip：28px 的位移会探出容器，不裁就闪一条横向滚动条；平时不挂，别牵连上面那个放大镜。 -->
      <div :class="{ 'overflow-x-clip': sliding }">
        <Transition :name="slideName" mode="out-in" @before-leave="sliding = true" @after-enter="sliding = false">
          <div :key="active" role="tabpanel" :aria-label="currentLabel" class="mt-4">
            <template v-if="active === 'detail'">
              <!-- 描述 → 规格参数 → 图文三节排在同一个 tab 里；哪一节空着就整节不出现，
                   三节全空才是下面那句「暂无商品信息」，别留一个只有标题的空格 -->
              <div class="space-y-6">
                <section v-if="product.productDesc">
                  <h2 class="mb-2 text-base font-semibold text-gray-900 dark:text-gray-100">{{ d.product.description }}</h2>
                  <!-- 后台那一格是 textarea 存的纯文本，所以这里不走 v-html -->
                  <p class="whitespace-pre-line rounded-lg border border-gray-200 bg-white p-4 text-sm leading-6 text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
                    {{ product.productDesc }}
                  </p>
                </section>

                <!-- 规格参数一行一条，行是服务端从 product_specs 那串 id 现查出来的（只有详情这条接口给） -->
                <section v-if="specRows.length">
                  <h2 class="mb-2 text-base font-semibold text-gray-900 dark:text-gray-100">{{ d.product.specs }}</h2>
                  <dl class="overflow-hidden rounded-lg border border-gray-200 bg-white text-sm dark:border-gray-700 dark:bg-gray-800">
                    <div
                      v-for="row in specRows"
                      :key="row.key"
                      class="flex gap-4 border-b border-gray-100 px-4 py-2.5 last:border-b-0 dark:border-gray-700"
                    >
                      <dt class="w-32 shrink-0 text-gray-500 dark:text-gray-400">{{ row.name }}</dt>
                      <dd class="min-w-0 break-words text-gray-800 dark:text-gray-200">{{ row.value || d.common.dash }}</dd>
                    </div>
                  </dl>
                </section>

                <!-- 详情图是后台那一档规格配的一列长图，按顺序铺、不限高（图片本身就是版式）；
                     只把整块收在 max-w-3xl 里，否则宽屏上一张 750px 的长图会被拉成糊的 -->
                <section v-if="detailShots.length">
                  <h2 class="mb-2 text-base font-semibold text-gray-900 dark:text-gray-100">{{ d.product.imageDetail }}</h2>
                  <div class="space-y-2">
                    <img
                      v-for="(url, index) in detailShots"
                      :key="url"
                      :src="url"
                      :alt="fill(d.product.detailImageAlt, { name: product.productName, n: index + 1 })"
                      class="mx-auto block w-full max-w-3xl rounded-lg"
                      loading="lazy"
                    />
                  </div>
                </section>
              </div>

              <p
                v-if="!product.productDesc && !specRows.length && !detailShots.length"
                class="py-10 text-center text-sm text-gray-500 dark:text-gray-400"
              >
                {{ d.product.noInfo }}
              </p>
            </template>

            <!-- 评论区公共组件：只吃「评的是哪一个」两格，取数/发表/点赞/删除都在它自己那一块里 -->
            <template v-else>
              <CommentSection preview :comment-type="1" :business-id="productId" />
            </template>
          </div>
        </Transition>
      </div>
    </template>

    <!-- 收藏那颗钉在根这一层：只吃 productId，页面正在加载还是加载失败都不影响它点得动 -->
    <FavoriteButton :target-type="1" :target-id="productId" :name="product?.productName" />
  </div>
</template>
