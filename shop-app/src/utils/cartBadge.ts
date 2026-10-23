import { getMyCart } from '@/api/cart'
import { tabbarList, tabbarStore } from '@/tabbar/store'
import { useTokenStore } from '@/store/token'

/**
 * 购物车 tab 上那颗角标：车里商品件数加起来，0 就不画（TabbarItem 判的是 `v-if="item.badge"`，0 是假值）。
 *
 * 角标走的是自定义 tabbar 的 badge（`src/tabbar/config.ts` 里那个注释掉的功能），
 * 所以只有一处写它 —— 这里，页面不自己去改 tabbarList，免得改到下标上（可见项会按角色过滤，下标会变）。
 * 它是提示不是数据：读失败就把上一趟的数留着，不能因为角标把页面变成红框。
 */
// tabbarList 上那一格是运行时补过 '/' 的 string（见 tabbar/store.ts），声明类型却是去头斜杠的路径联合，
// 所以这里按 string 比，别让 TS 拿字面量去撞那个联合
const CART_PAGE: string = '/pages/cart/cart'

/** 购物车那一格在底栏里的下标（可见项会按角色过滤，所以只能现查） */
export function cartIdx() {
  return tabbarList.value.findIndex(item => item.pagePath === CART_PAGE)
}

/**
 * 车里总件数：底栏角标之外，商品详情页那颗悬浮购物车上的角标读的是这一格。
 * 它跟着 setCartBadge 一起刷，所以哪一页捞过一次车就有数，不用为它另发请求。
 */
export const cartCount = ref(0)

/** 已经有车行了就别再发一趟请求（购物车页 load 完直接把它递过来） */
export function setCartBadge(rows: { quantity: number | null }[]) {
  cartCount.value = rows.reduce((sum, one) => sum + Number(one.quantity ?? 0), 0)
  const idx = cartIdx()
  if (idx < 0) {
    return
  }
  tabbarStore.setTabbarItemBadge(idx, cartCount.value)
}

/** 退出登录、没登录时角标要收掉，不然下次进来还挂着别人那趟的数 */
export function clearCartBadge() {
  setCartBadge([])
}

export async function refreshCartBadge() {
  if (!useTokenStore().updateNowTime().hasLogin) {
    clearCartBadge()
    return
  }
  try {
    // 这一趟静默：读不到就留着上一趟的数，别因为角标弹一句后端那句「请先登录」
    setCartBadge(await getMyCart(true))
  }
  catch {
    // 同上，http 层这次的错不弹出来
  }
}
