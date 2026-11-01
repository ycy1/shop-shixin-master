<script setup lang="ts">
/**
 * 首页「潮玩商品卡片」那一排：一整条缓慢平移到头再从头接上，中间不歇（不是通用商品页那圈 3D 环）。
 *
 * 走的是 CSS 动画（translate3d + linear infinite），不是滚 scrollLeft、也不是「滑一格停几秒」：
 * 逐帧写 scrollLeft 是主线程活，容易被 scroll-snap 拽一把；按格跳则会一下一下地停。
 * 交给合成层，浏览器自己插帧 —— 看着是"一直在慢慢走"。
 *
 * 停的时机只有三种，都跟"人现在要动手/在看"有关：鼠标停在这一排上（:hover）、
 * 键盘焦点进到某一格（:focus-within）、手指按在屏上（touchstart → track--hold）。
 * 其余时候一直走，包括切换页面回来（动画是 CSS 的，不靠定时器）。
 *
 * 首尾闭环（他 2026-10-29 的要求，这一版沿用）：卡片排两份，一份长 = count 格，
 * 动画位移正好推一份的长度 ⇒ 第二份的开头和第一份逐像素一样，接缝处看不出重来。
 * 位移按「一份的像素长」算（count × 一步），不用百分比：flex gap 也算得进去，用 50% 会差一个间距。
 *
 * 只有一份长过可视宽度才转（railable）：卡片本来就摆得下，再转就是把人眼前那一排挪走。
 *
 * 动效常开，不套 prefers-reduced-motion（这台机器系统里关了动效，一套整段就不动了）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import ProductCard from '@/views/product/ProductCard.vue';
import type { WebProduct } from '@/lib/products';

const props = withDefaults(
  defineProps<{ products: WebProduct[]; basePath?: string }>(),
  { basePath: '/product' },
);

/** 一张卡片占多宽（和下面 w-64 对齐）：一格就是一张卡 + 那一格间距 */
const CARD_WIDTH = 256;
const GAP = 16;
/** 走多快：每秒多少像素。一步 272px ⇒ 大约 5 秒挪过一张卡，来回盯一会儿才看得出在动 */
const SPEED_PX_PER_SEC = 55;

const viewport = ref<HTMLElement | null>(null);
/** 手指按着：手机上 hover 不成立，得单独记一格 */
const touching = ref(false);
/** 可视宽度，量一次 + 跟窗口变化；判断"值不值得转"用 */
const viewWidth = ref(0);

const step = CARD_WIDTH + GAP;
const count = computed(() => props.products.length);
/** 一份就铺满还溢出才转：不然转的是人眼前那一排，看着像卡片自己跑了 */
const railable = computed(() => count.value > 1 && count.value * step > viewWidth.value);
/** 两份接在一起：动画推一份的长度 = 推回起点的画面，这就是那个环 */
const railItems = computed(() => {
  const list = props.products;
  if (!railable.value) return list.map((item) => ({ item, key: `a-${item.id}` }));
  return [
    ...list.map((item) => ({ item, key: `a-${item.id}` })),
    ...list.map((item) => ({ item, key: `b-${item.id}` })),
  ];
});

/** 一份的像素长 + 按它算走完要多久：两者都交给 CSS 那两格变量，关键帧只认变量不认写死的数 */
const railStyle = computed(() => {
  const shift = count.value * step;
  return {
    '--rail-shift': `${shift}px`,
    '--rail-duration': `${Math.max(20, Math.round(shift / SPEED_PX_PER_SEC))}s`,
  };
});

function measure() {
  viewWidth.value = viewport.value?.clientWidth ?? 0;
}

onMounted(() => {
  measure();
  window.addEventListener('resize', measure);
});
onUnmounted(() => {
  window.removeEventListener('resize', measure);
});
</script>

<template>
  <div class="relative" @touchstart.passive="touching = true" @touchend="touching = false" @touchcancel="touching = false">
    <div ref="viewport" class="overflow-hidden pb-2" :class="{ 'rail-fade': railable }">
      <div
        class="track flex gap-4"
        :class="{ 'track--run': railable, 'track--hold': touching }"
        :style="railStyle"
      >
        <div v-for="one in railItems" :key="one.key" class="w-64 shrink-0">
          <ProductCard :product="one.item" :base-path="props.basePath" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 位移交给合成层插帧；will-change 提前把这一条抬成独立层 */
.track {
  will-change: transform;
}

/* 只在"值得转"的时候挂这条动画（railable 由 JS 量出来）。匀速、无限：一趟走完直接从头接上 */
.track--run {
  animation: rail-scroll var(--rail-duration, 60s) linear infinite;
}

/* 三种"人要动手/在看"的时机把它按停：指针悬停、键盘焦点进来、手指按着 */
.track--run:hover,
.track--run:focus-within {
  animation-play-state: paused;
}

.track--run.track--hold {
  animation-play-state: paused;
}

/* 两头渐隐：轨道一直在走，卡片走到边上不该被 overflow 硬切掉半张 —— 用一条 alpha 掩膜把两端擦淡。
   只在真的在转（railable）时挂：卡片本来就摆得下时不转，左边那格压暗只是白吃掉第一张的脸。
   宽度就这一格（--rail-fade），觉得淡得不够或擦得太多只动它。 */
.rail-fade {
  --rail-fade: 20px;
  -webkit-mask-image: linear-gradient(
    to right,
    transparent 0,
    #000 var(--rail-fade),
    #000 calc(100% - var(--rail-fade)),
    transparent 100%
  );
  mask-image: linear-gradient(
    to right,
    transparent 0,
    #000 var(--rail-fade),
    #000 calc(100% - var(--rail-fade)),
    transparent 100%
  );
}

@keyframes rail-scroll {
  to {
    transform: translate3d(calc(-1 * var(--rail-shift, 0px)), 0, 0);
  }
}
</style>
