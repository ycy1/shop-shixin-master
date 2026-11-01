import type { Ref } from 'vue'

/**
 * 「水滴」指示块（方案 A · 胶囊拉伸）的那点几何。
 *
 * 位置一律只递 transform，不递 left / width：那两条都要重排，Chromium（Edge 就是这一档内核）没法把它们
 * 交给合成器，主线程一有空活（切 tab 时下面整块内容重挂）动画就整段停住、忙完了再补帧 —— 看着就是「很慢、很黏」。
 * transform 是合成器驱动的，页面再忙珠子也照着自己的节奏走。⇒ 元素本身 left:0、width 固定成一格，
 * 移动和形变全用 translateX + scaleX 表达，过渡只声明 transform 一条。
 *
 * translateX 的百分比是「相对自己那格的宽」，所以第 i 格的左端就是 i*100%；配合 transform-origin: left center，
 * 跨度帧左端钉在起始格、宽度盖满整条跨度，收住那一帧右端不动、左端滑过去 —— 正是「拉长 → 收住」。
 *
 * 位置一律递百分比，不递 rpx：uni-h5 不给行内样式里的 rpx 换算（这坑记在项目的接线坑里），
 * 而圆角、高低、底色这些形状都写在 style/index.scss 的 .drop-pill 上，那边照旧用 rpx。
 * 前提是一排格子等宽：tabbar 那排每格 flex-1，dot 那排一格一 dot，顶部 tab 也按等分铺。
 */
export function useDroplet(
  current: Ref<number>,
  count: Ref<number>,
  /** stretch = 拉长扫过来（tabbar 和顶部 tab）；bead = 底槽里挤长再收圆（轮播 dot） */
  mode: 'bead' | 'stretch' = 'stretch',
) {
  /** 递给 .drop-pill 的内联样式（width 定成一格，移动全靠 transform；铺跨度那一拍多带一句 transition:none） */
  const style = ref('')

  function step() {
    return 100 / Math.max(1, count.value)
  }

  /** at = 落在第几格的左端（一格宽=100%），k = 撑成几格宽 */
  function write(at: number, k = 1, instant = false) {
    style.value = `width:${step().toFixed(4)}%;transform:translateX(${(at * 100).toFixed(4)}%) scaleX(${k.toFixed(4)});${instant ? 'transition:none;' : ''}`
  }

  function place(index: number) {
    write(index)
  }

  /**
   * bead：珠子滑到目标那格（带过冲的缓动，过头一点再弹回），路上撑到 2.25 倍宽，落位再收圆。
   * 这一档的原点在中间（见 .drop-pill--dot），所以撑长是对称的、不往右偏；最粗那一会也还在自己那一格里，
   * 顶不到底槽那 8rpx 内边距 —— 槽给的参照就够看出「挤长 → 收圆」。
   */
  let settleTimer: ReturnType<typeof setTimeout> | undefined
  function bead(index: number) {
    clearTimeout(settleTimer)
    write(index, 2.25)
    settleTimer = setTimeout(() => place(index), 420)
  }

  function stretch(from: number, to: number) {
    const lo = Math.min(from, to)
    const hi = Math.max(from, to)
    // 跨度那一帧：左端钉在起始格，宽度盖满 lo→hi（origin 在左，所以 translateX 用 lo、scaleX 用格数）
    write(lo, hi - lo + 1, true)
    // 跨度那一帧必须真被画出来，过渡才有起点可插值 —— 但**小程序逻辑层里没有 requestAnimationFrame**
    // （拿这个全局名直接抛 "requestAnimationFrame is not a function"，切 tabbar 那一下就是它），
    // 所以两脚改走「微任务把样式刷上去 → 下一拍定时器落回目标」：nextTick 保证跨度那一版进了这一次 setData，
    // 32ms 那一拍排在下一帧之后，形变照样有起点；定时器不像 rAF 那样会在页面切到后台时干脆不发，兜底那一条也就省了。
    let settled = false
    const settle = () => {
      if (settled) {
        return
      }
      settled = true
      place(to)
    }
    nextTick(() => setTimeout(settle, 32))
  }

  place(current.value)

  watch(current, (to, from) => {
    if (mode === 'bead') {
      if (from !== undefined && to !== from)
        bead(to)
      else
        place(to)
      return
    }
    // 没旧位置或压根没挪：没有跨度可拉长，直接贴到目标那一格
    if (from === undefined || to === from) {
      place(to)
      return
    }
    stretch(from, to)
  })

  // 按角色过滤后条数会变（tabbar 那排尤其）：条数一变就照新份数重新对齐，不做拉伸
  watch(count, () => place(current.value))

  return { style }
}
