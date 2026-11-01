import type { Ref } from 'vue';
import { nextTick, ref, watch } from 'vue';

/**
 * 「水滴」指示块（胶囊）。两种播法，由 mode 挑：
 * - stretch（tab / 菜单那一排）：先被拉成「旧位置 → 新位置」的一整条，再收到目标 —— 一头钉住只改长度。
 * - bead（dot 底下有一条半透明底槽时）：滑过去的同时被挤长、落位再收圆 —— 槽给了参照，才看得出水珠在管子里跑。
 *
 * 横排竖排都认：轨道的 flex-direction 是 column 就动 translateY + scaleY，否则 translateX + scaleX。
 * 个人中心那一列菜单两头都在变（lg 以下是横着滚的一排、lg 以上竖着排），写死一条轴就得在两处各留一份形状。
 *
 * 位置是**量出来的**（轨道内 .drop-item 的盒子），不要求各格等分 —— web 有 DOM，量一次比把标签摆成等宽便宜，
 * 也不用为了百分比去改各页原来那排的间距。（App 那一头量盒子要给小程序付一次异步查询，所以那边才是等分 + 百分比，
 * 见 shop-app/src/composables/useDroplet.ts。）
 *
 * stretch 有个坑：拉长那一帧必须真落进 DOM，过渡才有起点可插值 —— 先 await nextTick()（Vue 的样式补丁刷上去）、
 * 再读一次 offsetWidth 逼布局、最后才写目标。少了这两步就变成「从旧位置直接到新位置」的硬跳。
 *
 * 动的只有 transform，不是 left / width：那两条都是重排属性，Chromium 交不给合成器，页面一忙（换页要重挂内容、
 * 照片要重新栅格化）过渡就整段停住、忙完才补帧 —— 看着就是「没动画/很慢很黏」。所以盒子沿主轴的起点恒 0、
 * 长短定成目标那一格（这两样只在切换开始前一次性钉好，不参与过渡），移动和形变全用 translate + scale 表达。
 */
export function useDroplet(
  current: Ref<number>,
  track: Ref<HTMLElement | null | undefined>,
  mode: 'bead' | 'stretch' = 'stretch',
) {
  /** 递给 .drop-pill 的内联样式（沿主轴的长短钉成一格，移动全靠 transform；铺跨度那一拍多带一句 transition:none） */
  const style = ref('');

  /** 一格的两轴盒子：pos/size 沿主轴（排成一条的那个方向），cross/crossSize 是另一轴 —— 竖排时靠它铺满整行 */
  type Cell = { pos: number, size: number, cross: number, crossSize: number, vertical: boolean };

  function rects(): Cell[] | null {
    const host = track.value;
    if (!host)
      return null;
    const base = host.getBoundingClientRect();
    // 绝对定位照着**内边距盒**摆（left:0 / top:0 落在边框内侧），而 getBoundingClientRect 给的是外边框
    const border = { x: host.clientLeft, y: host.clientTop };
    const vertical = getComputedStyle(host).flexDirection.startsWith('column');
    return [...host.querySelectorAll<HTMLElement>('.drop-item')].map((el) => {
      const r = el.getBoundingClientRect();
      const box = { x: r.left - base.left - border.x, y: r.top - base.top - border.y };
      return vertical
        ? { pos: box.y, size: r.height, cross: box.x, crossSize: r.width, vertical }
        : { pos: box.x, size: r.width, cross: box.y, crossSize: r.height, vertical };
    });
  }

  /** pos = 沿主轴的起点，size = 沿主轴的长（钉成目标那一格，不参与过渡），k = 沿主轴撑成几倍长 */
  function write(cell: Cell, pos: number, size: number, k = 1, instant = false) {
    const at = instant ? 'transition:none;' : '';
    style.value = cell.vertical
      // 竖排的原点得改到上边（跨度要「上端钉住、下端扫过去」）；横排那几档的原点各不相同，交给各自的 CSS
      ? `transform-origin:center top;top:0;left:${cell.cross.toFixed(2)}px;width:${cell.crossSize.toFixed(2)}px;height:${size.toFixed(2)}px;transform:translateY(${pos.toFixed(2)}px) scaleY(${k.toFixed(4)});${at}`
      : `left:0;width:${size.toFixed(2)}px;transform:translateX(${pos.toFixed(2)}px) scaleX(${k.toFixed(4)});${at}`;
  }

  /**
   * 量一次、贴上去。instant 那一笔的意思是「这不算一次切换」—— 首屏第一次落位、条数后到把标签撑宽、
   * 缩放/字体回来重量 —— 这些都不该播动画：搜索页那一排是居中的，条数一到整排重新居中，
   * 跟着重贴的胶囊要是还带着过渡，就成了「一进页面下划线自己由短变长滑一下」，看着像切了一次 tab。
   */
  function pin(index: number, instant = false) {
    const one = rects()?.[index];
    if (one)
      write(one, one.pos, one.size, 1, instant);
  }

  /** 给调用方（挂载、尺寸变了、条数后到）：重新贴，但不放过渡 */
  function place() {
    pin(current.value, true);
  }

  /**
   * bead：整颗滑到目标那格（带过冲的缓动，过头一点再弹回），路上挤长、落位再收圆。
   * 面子是长边两头各内缩 4px（见 .drop-pill--dot .drop-pill__face），所以「面子刚好铺满一格」的倍长就是
   * size / (size - 8)；原点在中间，挤长才是对称的、不会一路往一头偏出去。
   */
  let settleTimer: ReturnType<typeof setTimeout> | undefined;

  function bead(to: number) {
    const one = rects()?.[to];
    if (!one)
      return;
    clearTimeout(settleTimer);
    write(one, one.pos, one.size, one.size / (one.size - 8));
    settleTimer = setTimeout(() => write(one, one.pos, one.size), 420);
  }

  async function stretch(from: number, to: number) {
    const rs = rects();
    const a = rs?.[from];
    const b = rs?.[to];
    if (!a || !b) {
      place();
      return;
    }
    const lo = Math.min(a.pos, b.pos);
    const hi = Math.max(a.pos + a.size, b.pos + b.size);
    // 跨度那一帧：长短先一次钉成目标那一格（不参与过渡，这一拍改它不掉相），起点钉住、scale 盖满整条跨度
    write(b, lo, b.size, (hi - lo) / b.size, true);
    // style 只是个 ref：改完不等于 DOM 改完，Vue 要等这一轮微任务刷完才把补丁写上去。
    // 所以必须 await nextTick()（跨度那一版真的落到 DOM）→ 再读一次 offsetWidth 逼布局 → 最后写目标。
    // 少了 nextTick 这一步，跨度那一版在同一次刷新里就被目标覆盖，浏览器只看见「从旧位置到新位置」
    // ⇒ 没有拉长过程，就是硬跳一下（dot 那颗一直「没水滴效果」就是这个）。
    await nextTick();
    void track.value?.offsetWidth;
    write(b, b.pos, b.size);
  }

  watch(current, (to, from) => {
    if (mode === 'bead') {
      if (from !== undefined && to !== from)
        bead(to);
      else
        pin(to);
      return;
    }
    // 没旧位置或压根没挪：没有跨度可拉长，直接贴到目标那一格
    if (from === undefined || to === from)
      pin(to);
    else
      void stretch(from, to);
  });

  // 换档不改长短就没得插值，第一次量到位置即可（挂载时机由调用方的 ref 决定，这里不自己监听）
  watch(() => track.value, () => place());

  return { style, place };
}
