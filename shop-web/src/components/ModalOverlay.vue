<script setup lang="ts">
import { useBodyScrollLock } from '@/lib/scroll-lock';

/**
 * 弹窗外面那一圈遮罩：这一页八张弹窗用的都是同一段类 + 同一句「点空白处关」，抄到第八处就收一起
 *（口径同 ConfirmDialog、ImagePreview 那两次抽公共件）。
 *
 * 锁滚动挂在这里面 —— 这一层挂起来就等于弹窗开着、卸掉就是关了，所以调用方一行都不用写。
 *
 * 两种形状：
 * - 卡片在里面（绝大多数）：`role="dialog"` / `aria-label` 留在那张白卡上，这一层只当遮罩，别套两层 dialog；
 * - 没有卡片（图片预览那种铺满一层的）：传 `aria-label`，这一层自己就是那个对话框，`overlay-class` 顺手换深浅和层级。
 *
 * 开合一律由调用方 v-if 控制（和这一页另外那几颗同一个用法，也是这一版 flowbite FwbModal 的用法），
 * 所以这里不留 visible 那一格。
 */
const props = withDefaults(
  defineProps<{
    /** 空 = 这一层只是遮罩，弹窗语义由里面那张卡片自己担 */
    ariaLabel?: string;
    /** 整段类名（默认是这一页的统一形状：半黑、z-50、居中、四周留一格边） */
    overlayClass?: string;
  }>(),
  {
    ariaLabel: '',
    overlayClass: 'fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4',
  },
);

const emit = defineEmits<{ close: [] }>();

useBodyScrollLock();
</script>

<template>
  <div
    :class="props.overlayClass"
    :role="props.ariaLabel ? 'dialog' : undefined"
    :aria-modal="props.ariaLabel ? 'true' : undefined"
    :aria-label="props.ariaLabel || undefined"
    @click.self="emit('close')"
  >
    <slot />
  </div>
</template>
