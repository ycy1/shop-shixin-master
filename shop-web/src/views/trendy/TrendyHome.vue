<script setup lang="ts">
/**
 * 潮玩专区首页：自上而下四块 —— 盲盒机（两台老虎机）、潮玩商品卡片、公园轮播图、所有潮玩商品。
 *
 * 盲盒卡片那一排原先有、现在不摆了：机台上那两台 + 「看全部盲盒」那颗已经够入口，
 * 首页再铺一排同样的盒子只是把货架往下推。整排盲盒去 /trendy/box 那一页看。
 *
 * 三趟读各管一块，谁挂了只红自己那一块（盲盒接口挂了不该让公园轮播也跟着空）；
 * 首屏一次并发发出去，不排队等。
 * 玩法/状态那两档称呼走后端字典（lib/dict.ts），卡片自己会读，这一层只负责把字典要回来一趟。
 *
 * 公园轮播和商品卡片都落在专区自己的详情页上（/trendy/park/{id}、/trendy/product/{id}），
 * 不落到通用商品页 —— 为的是点进去之后左侧那条导航还在。
 * 轮播与老虎机的动效常开、不套 prefers-reduced-motion 兜底（这台机器系统里关了动效，一套就整段不动）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import DotTrack from '@/components/DotTrack.vue';
import ProductCard from '@/views/product/ProductCard.vue';
import ProductRail from './ProductRail.vue';
import SlotMachine from './SlotMachine.vue';
import { useI18n } from '@/i18n';
import { fetchProducts, type WebProduct } from '@/lib/products';
import { loadDict } from '@/lib/dict';
import { fetchTrendyBoxes, fetchTrendyParks, type Park, type TrendyBox } from '@/lib/trendy';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
defineProps<{ locale?: string }>();

const { d, fill, href } = useI18n();
const t = d.trendy;

/** 专区里那两排商品都跳潮玩自己的详情页（通用商品页没有这条左侧导航） */
const PRODUCT_BASE = '/trendy/product';

/** 首页这一屏各块的取数条数：机台 2 台、货架 24 件（机台那两枚由接口按热门截好，不再多捞） */
const MACHINE_COUNT = 2;
const PRODUCT_SIZE = 24;

/**
 * 机台上那两台：接口按 hot=1 查回来的，运营在后台勾了哪两台这里就是哪两台。
 * 一台都没勾时 hot 那一趟是空的，退回不带条件的第二趟（进行中的本来排在前面），机台不能空着。
 */
const machines = ref<TrendyBox[]>([]);
const boxError = ref('');
const boxLoading = ref(true);

/** 公园轮播同理只看热门的；同样留一条「一家都没勾就摆全部」的退路，那一块不能空着 */
const carouselParks = ref<Park[]>([]);
const parkError = ref('');
const parkLoading = ref(true);

const products = ref<WebProduct[]>([]);
const productError = ref('');
const productLoading = ref(true);

/** 潮玩商品那一排转着看，多给几件；底下那一整架是全量 */
const featured = computed(() => products.value.slice(0, 12));

/**
 * 机台那两台：接口按 hot=1 查回来的，运营在后台勾了哪两台这里就是哪两台。
 * 一台都没勾时 hot 那一趟是空的，退回不带条件的第二趟（进行中的本来排在前面），机台不能空着。
 *
 * silent 那一档是「抽完补一遍数据」用的：不切加载态、失败也不写 boxError ——
 * 那一趟的目的是把剩余次数/状态刷新，失败了顶多停在抽之前那一格，别把人刚抽出来的结果弹窗顶掉。
 */
async function loadBoxes(silent = false) {
  if (!silent) {
    boxLoading.value = true;
    boxError.value = '';
  }
  try {
    const hot = await fetchTrendyBoxes({ hot: 1, pageNum: 1, pageSize: MACHINE_COUNT });
    const page = (hot.records?.length ?? 0)
      ? hot
      : await fetchTrendyBoxes({ pageNum: 1, pageSize: MACHINE_COUNT });
    machines.value = page.records ?? [];
  } catch (error) {
    if (!silent) boxError.value = error instanceof Error ? error.message : String(error);
  } finally {
    if (!silent) boxLoading.value = false;
  }
}

async function loadParks() {
  parkLoading.value = true;
  parkError.value = '';
  try {
    const hot = await fetchTrendyParks(1);
    carouselParks.value = hot.length ? hot : ((await fetchTrendyParks()) ?? []);
  } catch (error) {
    parkError.value = error instanceof Error ? error.message : String(error);
  } finally {
    parkLoading.value = false;
  }
}

async function loadProducts() {
  productLoading.value = true;
  productError.value = '';
  try {
    const page = await fetchProducts({ trendyOnly: 1, pageNum: 1, pageSize: PRODUCT_SIZE });
    products.value = page.records ?? [];
  } catch (error) {
    productError.value = error instanceof Error ? error.message : String(error);
  } finally {
    productLoading.value = false;
  }
}

/* ---------- 公园轮播：一张铺满那一块，dot / 左右颗都只动 index ---------- */
const slide = ref(0);
const AUTOPLAY_MS = 4500;
let timer: ReturnType<typeof setInterval> | undefined;

const slideCount = computed(() => carouselParks.value.length);
function goSlide(n: number) {
  if (slideCount.value === 0) return;
  slide.value = ((n % slideCount.value) + slideCount.value) % slideCount.value;
}
function startTimer() {
  stopTimer();
  // 只有一张也挂这一格无所谓：下面画不画 dot 由 slideCount>1 决定
  timer = setInterval(() => goSlide(slide.value + 1), AUTOPLAY_MS);
}
function stopTimer() {
  if (timer) clearInterval(timer);
  timer = undefined;
}

onMounted(() => {
  loadDict(['trendy_box_type', 'trendy_box_status']);
  void loadBoxes();
  void loadParks();
  void loadProducts();
  startTimer();
});
onUnmounted(stopTimer);
</script>

<template>
  <div class="flex flex-col gap-10">
    <!-- 一、盲盒机：三根轴的老虎机，转一下预览这台盒里的款式（不记抽盒） -->
    <section>
      <header class="mb-3 flex items-baseline justify-between gap-4">
        <div>
          <h2 class="text-lg font-semibold">{{ t.home.machineTitle }}</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ t.home.machineSub }}</p>
        </div>
        <a :href="href('/trendy/box')" class="shrink-0 text-sm text-blue-600 hover:underline dark:text-blue-400">
          {{ t.home.moreBox }} ›
        </a>
      </header>

      <p v-if="boxError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ fill(t.home.loadFailed, { msg: boxError }) }}
        <button type="button" class="ml-2 underline" @click="loadBoxes()">{{ t.home.retry }}</button>
      </p>
      <p v-else-if="boxLoading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
      <p v-else-if="!machines.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.home.machineEmpty }}</p>

      <!-- 两台并排（窄屏一台一行）：各一台盒、各自一副轴和把手，互不影响。
           首页这两台也是真抽（2026-10-30 他点的）：登录后按下去就走 draw 那条，抽中的款由服务端加进购物车；
           没登录时那颗按钮挂 data-open-login，点击归页头登录窗。抽完静默补一遍机台数据（剩余次数/状态会变），
           不切「加载中」、失败了也不顶掉机台和结果弹窗。 -->
      <div v-else class="grid gap-10 lg:grid-cols-2 lg:gap-14">
        <SlotMachine v-for="box in machines" :key="box.id" :box="box" mode="draw" @drawn="loadBoxes(true)" />
      </div>
    </section>

    <!-- 二、潮玩商品卡片：挂了潮玩系列的商品，平铺一排自动往前转（不用通用首页那圈 3D 环） -->
    <section>
      <header class="mb-3">
        <h2 class="text-lg font-semibold">{{ t.home.productTitle }}</h2>
        <p class="text-sm text-gray-500 dark:text-gray-400">{{ t.home.productSub }}</p>
      </header>
      <p v-if="productError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ fill(t.home.loadFailed, { msg: productError }) }}
        <button type="button" class="ml-2 underline" @click="loadProducts">{{ t.home.retry }}</button>
      </p>
      <p v-else-if="productLoading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
      <p v-else-if="!featured.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.home.empty }}</p>
      <ProductRail v-else :products="featured" :base-path="PRODUCT_BASE" />
    </section>

    <!-- 三、公园轮播图：营业中的园区，一张铺满，dot 切园区，点开进那家园区的详情页 -->
    <section>
      <header class="mb-3 flex items-baseline justify-between gap-4">
        <div>
          <h2 class="text-lg font-semibold">{{ t.home.parkTitle }}</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ t.home.parkSub }}</p>
        </div>
        <a :href="href('/trendy/park')" class="shrink-0 text-sm text-blue-600 hover:underline dark:text-blue-400">
          {{ t.home.morePark }} ›
        </a>
      </header>

      <p v-if="parkError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ fill(t.home.loadFailed, { msg: parkError }) }}
        <button type="button" class="ml-2 underline" @click="loadParks">{{ t.home.retry }}</button>
      </p>
      <p v-else-if="parkLoading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
      <p v-else-if="!carouselParks.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.home.empty }}</p>

      <div
        v-else
        class="relative overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-700"
        @mouseenter="stopTimer"
        @mouseleave="startTimer"
      >
        <div
          class="flex transition-transform duration-500 ease-out"
          :style="{ transform: `translateX(-${slide * 100}%)` }"
        >
          <a
            v-for="park in carouselParks"
            :key="park.id"
            :href="href(`/trendy/park/${park.id}`)"
            class="relative aspect-[16/7] w-full shrink-0"
          >
            <img
              v-if="park.coverImage"
              :src="park.coverImage"
              :alt="park.parkName"
              class="h-full w-full object-cover"
            />
            <span
              v-else
              class="flex h-full w-full items-center justify-center bg-gray-100 text-sm text-gray-400 dark:bg-gray-700"
            >
              {{ t.parkCard.noImage }}
            </span>
            <span
              class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent px-6 py-4 text-white"
            >
              <span class="block text-lg font-semibold">{{ park.parkName }}</span>
              <span class="block text-sm opacity-90">
                {{ [park.areaZh, park.openTime].filter(Boolean).join(' · ') }}
                <template v-if="park.priceMin != null">
                  · {{ fill(t.parkCard.priceFrom, { p: Number(park.priceMin).toFixed(2) }) }}
                </template>
              </span>
            </span>
          </a>
        </div>

        <button
          v-if="slideCount > 1"
          type="button"
          class="absolute left-3 top-1/2 -translate-y-1/2 rounded-full bg-black/40 px-3 py-1 text-white hover:bg-black/60"
          :aria-label="t.parkCard.prev"
          @click="goSlide(slide - 1)"
        >
          ‹
        </button>
        <button
          v-if="slideCount > 1"
          type="button"
          class="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-black/40 px-3 py-1 text-white hover:bg-black/60"
          :aria-label="t.parkCard.next"
          @click="goSlide(slide + 1)"
        >
          ›
        </button>
        <!-- 指示点：和活动/商品/门店那几条轮播同一个组件（半透明圆角槽 + 在里面跑的一颗水珠），
             铺在照片上所以走 photo 那一档底子；条数只有一张时不画（组件自己也判了一次）。
             外面必须再套一层来定位：组件根自己的 position 是 relative（水珠以它为包含块），
             直接往组件上写 absolute 的话两条工具类谁生效看样式表顺序，实测被 relative 压掉了 ——
             整条槽掉进正常流里横在轮播底下，就是「位置乱了」。 -->
        <div class="absolute bottom-3 right-6">
          <DotTrack
            :count="slideCount"
            :current="slide"
            :label="t.parkCard.gotoSlide"
            @select="goSlide"
          />
        </div>
      </div>
    </section>

    <!-- 四、所有潮玩商品：整架 -->
    <section>
      <h2 class="mb-3 text-lg font-semibold">{{ t.home.shelfTitle }}</h2>
      <p v-if="productError" class="text-sm text-red-600 dark:text-red-400">
        {{ fill(t.home.loadFailed, { msg: productError }) }}
      </p>
      <p v-else-if="productLoading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
      <p v-else-if="!products.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.home.empty }}</p>
      <div v-else class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        <ProductCard v-for="item in products" :key="item.id" :product="item" :base-path="PRODUCT_BASE" />
      </div>
    </section>
  </div>
</template>
