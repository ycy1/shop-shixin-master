<script setup lang="ts">
/**
 * 门店那一圈图片：整宽平铺 + 底部指示点。
 *
 * 没用 flowbite-vue 的 FwbCarousel：它把轨道高度写死成 h-56/sm:h-64/xl:h-80，图片是绝对定位居中的，
 * 既没有 class 也没有插槽能改（.d.ts 里 props 只有 pictures/noIndicators/noControls/slide/slideInterval/animation，
 * 而且那个 animation 声明了却没被渲染读，切帧是 z-index 互换、没有过渡）—— 这一格要的是整宽，套不进去。
 * 也没复用首页那颗 ProductCarousel：那是绕 Y 轴的 3D 环，状态全在下标和转角上，跟平面平移不是一回事。
 *
 * 只吃已经拆好的 url 数组（脏数据由调用方用 parseImageUrls 挡掉），一张也不画自己那格的判断，
 * 所以 images 为空时整块不渲染 —— 不留一条空轨道。
 */
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useI18n } from '@/i18n';

const props = defineProps<{ images: string[]; name: string }>();
/** 这一颗不是岛，语种由门店详情页那棵 provide 下来（见 @/i18n 顶上那段） */
const { d, fill } = useI18n();

const AUTOPLAY_MS = 3500;

const active = ref(0);
/** 指针压在这一块上就别自己往前走：正要点某一张时被换掉，点下去就成了另一张 */
const hovered = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;

function go(index: number) {
  active.value = Math.max(0, Math.min(props.images.length - 1, index));
}

function next() {
  // 到尾一张接着回第一张：这是「一圈展示」，不是「一页一页翻」，不留一个按不动的尾端
  active.value = active.value >= props.images.length - 1 ? 0 : active.value + 1;
}

function step(delta: number) {
  const last = props.images.length - 1;
  const target = active.value + delta;
  // 只有一张时上面那颗组件根本不会渲染，这里就只管两头各绕回去
  go(target < 0 ? last : target > last ? 0 : target);
}

function startTimer() {
  stopTimer();
  if (props.images.length < 2) return;
  timer = setInterval(() => {
    if (!hovered.value) next();
  }, AUTOPLAY_MS);
}

function stopTimer() {
  if (timer) {
    clearInterval(timer);
    timer = undefined;
  }
}

// 换店（调用方给的那组 url 变了）要重来：下标可能停在上一家店的那圈之外，定时器也该重新起算
watch(
  () => props.images,
  () => {
    active.value = 0;
    startTimer();
  },
);

onMounted(startTimer);
onUnmounted(stopTimer);
</script>

<template>
  <section
    v-if="images.length"
    class="relative overflow-hidden rounded-lg border border-gray-200 bg-gray-100 dark:border-gray-700 dark:bg-gray-800"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <!-- 整宽平铺：轨道按 100% 一格平移，所以每格钉 w-full shrink-0，高度由这一格自己的 aspect 定 -->
    <div
      class="flex aspect-[16/9] transition-transform duration-500 ease-out sm:aspect-[21/9]"
      :style="{ transform: `translateX(-${active * 100}%)` }"
    >
      <img
        v-for="(url, index) in images"
        :key="url"
        :src="url"
        :alt="fill(d.store.carousel.imageAlt, { name, n: index + 1 })"
        class="h-full w-full shrink-0 grow-0 basis-full object-cover"
        loading="lazy"
        :aria-hidden="index !== active"
      />
    </div>

    <!-- 指示点：这一格是整块轮播的当前位置，所以给 aria-label 说清是第几张 -->
    <div v-if="images.length > 1" class="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-2">
      <button
        v-for="(url, index) in images"
        :key="url"
        type="button"
        :aria-label="fill(d.store.carousel.gotoSlide, { n: index + 1 })"
        :aria-current="index === active"
        class="h-2 w-2 rounded-full transition-colors"
        :class="index === active ? 'bg-white' : 'bg-white/50 hover:bg-white/80'"
        @click="go(index)"
      ></button>
    </div>

    <button
      v-if="images.length > 1"
      type="button"
      :aria-label="d.store.carousel.prev"
      class="absolute left-2 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white hover:bg-black/50"
      @click="step(-1)"
    >
      <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="m15 6-6 6 6 6" />
      </svg>
    </button>
    <button
      v-if="images.length > 1"
      type="button"
      :aria-label="d.store.carousel.next"
      class="absolute right-2 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white hover:bg-black/50"
      @click="step(1)"
    >
      <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="m9 6 6 6-6 6" />
      </svg>
    </button>
  </section>
</template>
