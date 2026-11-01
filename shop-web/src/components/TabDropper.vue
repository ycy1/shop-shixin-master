<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useDroplet } from '@/lib/droplet';

/**
 * tab 排 + 水滴胶囊。胶囊的位置是量出来的（轨道里每颗 .drop-item 的盒子），所以**各页那一排不必等分**：
 * 原来的 gap / justify 都还留在页面上，选中项底下那根 border-b-2 换成会拉长再收住的水滴。
 *
 * 形状（高低、圆角、内缩、底色）在 global.css 的 .drop-pill 上 —— 不放组件自己的 <style scoped>：
 * 那份只在 island 头一次加载时注入，跳走再点回来就没了（理由同 .plaza-cols 那一段）。
 */
const props = defineProps<{
  items: { key: string, label: string, count?: number | null }[];
  modelValue: string;
  /** 那一排给读屏软件的说辞（原来各页写在 role="tablist" 那格上，挪进组件后得递进来才不丢） */
  label?: string;
  /** 轨道自己的间距/对齐（各页原来写的 gap-6、justify-center、mb-4 不一样，交回调用方递） */
  listClass?: string;
  /** underline = 底下那根线就是水滴（详情/搜索那种）；wash = 压在选中文字背后的一块软底（排序那种本来没有线） */
  shape?: 'underline' | 'wash';
}>();

const emit = defineEmits<{
  'update:modelValue': [key: string];
}>();

const track = ref<HTMLElement | null>(null);
const index = computed(() => {
  const at = props.items.findIndex(one => one.key === props.modelValue);
  return at < 0 ? 0 : at;
});

const { style: pillStyle, place } = useDroplet(index, track);

/**
 * 量到位置了才让水珠接管。SSR 那一张纸上内联样式是空的（没有盒子可量），
 * 那一段要是没人画线，「第一次进这一页」选中那颗底下就是光秃秃的 —— 所以线先由按钮自己的 border-b 顶着，
 * 这一格一变 true，global.css 的 .drop-live 就把那根静态线收掉（同一拍里交棒，看不出换人）。
 */
const live = computed(() => pillStyle.value !== '');

onMounted(() => {
  place();
  // 标签上那个条数是异步回来的（评论(1)），宽度一变胶囊就停在旧位置 ⇒ 轨道尺寸一变就重新贴一次。
  // 只做 place 不做 stretch：数据晚到不该看起来像「切了一次 tab」。
  const ro = new ResizeObserver(() => place());
  if (track.value) ro.observe(track.value);
  onUnmounted(() => ro.disconnect());
});
// 这一排是占满一行的（justify-center 那种），标签变宽时轨道自己的盒子尺寸没变 ⇒ 光靠 ResizeObserver 等不到，
// 所以 items 一变（条数回来了）就下一拍重量一次。
watch(() => props.items, () => void nextTick(place));
// 换页/缩放/字体回来晚了都会让量到的盒子失效 ⇒ 重新贴一次（不做拉伸，免得resize途中播一段怪形变）
if (typeof window !== 'undefined') window.addEventListener('resize', place);
</script>

<template>
  <div
    ref="track"
    role="tablist"
    :aria-label="props.label"
    class="relative flex"
    :class="[props.shape === 'wash' ? '' : 'border-b border-gray-200 dark:border-gray-700', live ? 'drop-live' : '', props.listClass]"
  >
    <span class="drop-pill" :class="props.shape === 'wash' ? 'drop-pill--wash' : 'drop-pill--tab'" :style="pillStyle"><span class="drop-pill__face" /></span>
    <button
      v-for="one in props.items"
      :key="one.key"
      type="button"
      role="tab"
      class="drop-item -mb-px pb-2 text-sm font-medium transition-colors"
      :class="[
        props.shape === 'wash' ? '' : 'border-b-2 border-transparent',
        one.key === props.modelValue
          ? props.shape === 'wash' ? 'text-blue-600 dark:text-blue-400' : 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
          : 'text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200',
      ]"
      :aria-selected="one.key === props.modelValue"
      @click="emit('update:modelValue', one.key)"
    >
      {{ one.label }}<span v-if="one.count !== null && one.count !== undefined" class="ml-1 text-gray-400 dark:text-gray-500">({{ one.count }})</span>
    </button>
  </div>
</template>
