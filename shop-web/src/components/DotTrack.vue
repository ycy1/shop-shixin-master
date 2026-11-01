<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useI18n } from '@/i18n';
import { useDroplet } from '@/lib/droplet';

/**
 * 轮播的 dot：一颗会在自己那格里挤长的水珠，点第几颗就翻到第几张。
 *
 * 两档底子（tone）：
 * - photo（默认）= 铺在照片上，底下那条 `bg-white/25` 的半透明圆角槽既是参照也是范围 —— 之前那颗白点直接摊在
 *   照片上变长变短，两头没参照，感觉就是跳一下，槽一画出来才读得出「挤长 → 冲过头 → 收圆」。
 * - plain = 铺在白卡片上（首页「轮播推荐」那一圈），那条白槽在浅底上根本看不见，所以这档不铺底、
 *   点位用灰、水珠走站点主色（--dp-bg 在 global.css 的 .drop-dots--plain 上给）。
 *
 * 珠子位置按量（每颗 .drop-item 的盒子），所以槽可以随条数伸缩，各轮播不用各自对齐尺寸。
 */
const props = withDefaults(defineProps<{
  count: number
  current: number
  /** 每颗点给读屏软件的那句（带 {n} 占位），各页用自己的那一格文案 */
  label: string
  tone?: 'photo' | 'plain'
}>(), {
  tone: 'photo',
});

const emit = defineEmits<{ select: [index: number] }>();

const { fill } = useI18n();
const track = ref<HTMLElement | null>(null);
const current = computed(() => props.current);
const { style: pillStyle, place } = useDroplet(current, track, 'bead');

/**
 * 量到位置之前，当前那颗还照旧画一个点：SSR 那一趟没有盒子可量，水珠是空的，
 * 不然首屏选中那一格两边都看不见东西（和 TabDropper 那根静态线同一个道理）。
 */
const live = computed(() => pillStyle.value !== '');

function dotClass(index: number) {
  if (index === props.current && live.value)
    return 'bg-transparent';
  return props.tone === 'plain'
    ? 'bg-gray-300 hover:bg-gray-400 dark:bg-gray-600 dark:hover:bg-gray-500'
    : 'bg-white/55 hover:bg-white/80';
}

// 条数是异步回来的（首屏之后才有），铺好 DOM 再贴一次，不然珠子还停在没量到的位置
watch(() => props.count, () => void nextTick(place));
onMounted(() => place());
</script>

<template>
  <div
    v-if="props.count > 1"
    ref="track"
    role="tablist"
    class="relative flex items-center rounded-full p-1"
    :class="props.tone === 'plain' ? 'drop-dots--plain' : 'bg-white/25'"
  >
    <span class="drop-pill drop-pill--dot" :style="pillStyle"><span class="drop-pill__face" /></span>
    <button
      v-for="(_, index) in props.count"
      :key="index"
      type="button"
      class="drop-item flex h-4 w-4 items-center justify-center"
      :aria-label="fill(props.label, { n: index + 1 })"
      :aria-current="index === props.current"
      @click="emit('select', index)"
    >
      <span class="h-2 w-2 rounded-full transition-colors" :class="dotClass(index)" />
    </button>
  </div>
</template>
