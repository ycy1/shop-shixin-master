import { onBeforeUnmount, onMounted } from 'vue';

/**
 * 弹窗开着的时候把底下那一页钉住（滚轮、触摸板、拖页面右侧那根条，三条路一起断）。
 *
 * 为什么是计数而不是布尔：这些弹窗会套着开（改资料那张里点头像开预览、点地图选点开地图），
 * 内层关掉时不能顺手把外层也解锁 —— 外层还开着，底下就还得钉着。
 *
 * 为什么补一条 padding-right：钉住 overflow 会把页面自己那根滚动条摘掉，内容横向跳一小格；
 * 量出来的那一小格（有滚动条才有值，触屏/覆盖式滚动条量出来是 0）垫回 padding 就看不出来了。
 */
let lockedCount = 0;
let previousOverflow = '';
let previousPaddingRight = '';

function acquire(): void {
  if (lockedCount++ > 0) return;
  bindSwapReset();
  const root = document.documentElement;
  previousOverflow = root.style.overflow;
  previousPaddingRight = root.style.paddingRight;
  const gap = window.innerWidth - root.clientWidth;
  root.style.overflow = 'hidden';
  if (gap > 0) root.style.paddingRight = `${gap}px`;
}

function release(): void {
  if (lockedCount === 0 || --lockedCount > 0) return;
  reset();
}

function reset(): void {
  lockedCount = 0;
  const root = document.documentElement;
  root.style.overflow = previousOverflow;
  root.style.paddingRight = previousPaddingRight;
}

/**
 * ClientRouter 换页是直接把岛所在的 DOM 拆掉，Vue 那一侧的卸载钩子未必赶得上（这一层挂在 layouts/Base.astro）——
 * 弹窗还开着就翻页，那一串内联样式会留在 `<html>` 上，新页面从此滚不动。
 * 所以换页之前自己把这一层收掉；监听只在浏览器那趟挂一次，挂之前不碰 document（SSR 那一趟没有它）。
 */
let swapBound = false;
function bindSwapReset(): void {
  if (swapBound) return;
  swapBound = true;
  document.addEventListener('astro:before-swap', reset);
}

/**
 * 钉一下 / 松一下都挂在生命周期上：只在 onMounted、onBeforeUnmount 里摸 document，
 * 岛在 setup 阶段动浏览器对象会撞 hydration 那条老坑。
 *
 * 调用方只有两处：`ModalOverlay`（这一页所有弹窗的遮罩都从那儿挂，所以正常没人该直接调这一颗）
 * 和 `ConfirmDialog`（它套的是 flowbite 的 FwbModal，遮罩不在我们手里，只能自己钉一下）。
 */
export function useBodyScrollLock(): void {
  onMounted(acquire);
  onBeforeUnmount(release);
}
