/**
 * 右侧栏和导航栏之间的那根线。
 *
 * 抽屉挂在布局那一层（每页一份实例），开关却有两处：布局自己认左滑，导航栏那颗图标在页面组件树里 —— 隔着组件层，
 * 只能用 provide/inject 递这一格。
 *
 * 递的是这一页布局实例上那一份，不是全局：tab 页切走时实例还活着，全局那一份会把开合串到别的页上去。
 */
export interface SideMenuHost {
  /** 拉开抽屉（和左滑开的是同一只） */
  open: () => void
}

export const SIDE_MENU_KEY = 'side-menu-host'

/**
 * 给 AppNavBar 用：拿回这一页的「开抽屉」。
 * 拿不到 host（这颗被摆在布局外面用）就回 null —— 组件照旧画，只是那一下点了没东西。
 */
export function useSideMenuEntry() {
  return inject<SideMenuHost | null>(SIDE_MENU_KEY, null)
}

/**
 * 「这一次手势是从页面里横滑那几块起的」那一笔登记。
 *
 * 横滑区（图轮播、横着滚的 chip 条）原来挂的是 `@touchstart.stop @touchend.stop` —— **不能这么写**：
 * 小程序把 `.stop` 编译成 catchtouch*，而微信的 tap 是靠 touchstart/touchend 往上冒才合成出来的，
 * 被 catch 掉之后那一片子元素的 `@click` 整个不响（首页门店、品牌点不进详情页就是这一条），
 * H5 反倒看不出问题，因为那边 `.stop` 只是 stopPropagation。
 *
 * 所以改成「只登记、不拦」：容器自己记一笔，布局那一层读一次就清零 —— 抽屉照旧不吃横滑，点击照旧走。
 * 子元素那层先跑、冒到布局才读，顺序是事件冒泡给的，不用等定时器。
 */
let swipeZoneTouched = false

export function markSwipeZone() {
  swipeZoneTouched = true
}

export function takeSwipeZone() {
  const hit = swipeZoneTouched
  swipeZoneTouched = false
  return hit
}
