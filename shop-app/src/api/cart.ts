import type { IShopCartRow, IShopCartSaveForm } from './types/shop'
import { http } from '@/http/http'

/**
 * 购物车（App 端）。五条都要求登录：后端 /api/** 不过 Sa-Token 拦截器，
 * “必须登录”由服务层第一句 requireLoginUser 当场拒掉，回包是业务码 500 那一档，
 * http.ts 会自动 toast，页面不用另写一套判断。
 *
 * @param silent 只给 tabbar 角标那一趟用：它是个提示不是数据，读不到留着上一趟的数就行，
 *               不该因为角标在冷启动的时候弹一句「请先登录」。
 */
export function getMyCart(silent = false) {
  return http.get<IShopCartRow[]>('/api/app/shop/cart/list', undefined, undefined, { hideErrorToast: silent })
}

/** 同一规格重复点是叠数量，不会报重复 */
export function addToCart(form: IShopCartSaveForm) {
  return http.post<void>('/api/app/shop/cart/add', form)
}

export function updateCart(form: IShopCartSaveForm) {
  return http.put<void>('/api/app/shop/cart/update', form)
}

/** 全选/取消全选：后端一句 UPDATE 按人刷完名下所有行，不让前端逐行 PUT。selected 是 query 参数（@RequestParam），不是请求体 */
export function setAllCartSelected(selected: 0 | 1) {
  return http.put<void>('/api/app/shop/cart/selectAll', undefined, { selected })
}

/**
 * 这一行按多少钱算：车里的 price 优先（抽中入车的那几条挂的是盲盒单抽价），
 * 那一格没写过（老行、后台代客加购的行）才退回这条规格的现价 productPrice。
 *
 * 统一下单取价读的是同一个格子（服务端按 cartId 去查那一行），所以页面上显示的、合计里加的
 * 和最后真收的钱是同一个数 —— 两边各写一遍 ?? 迟早算出两个说法。
 */
export function payPriceOf(one: IShopCartRow) {
  return Number(one.price ?? one.productPrice ?? 0)
}

/**
 * 这一行是不是一整盒（端盒那一趟在车里就是一行）：库里那一行不挂在某条规格上，
 * skuId 落的是 0 这个哨兵，盒内有哪几款各几件看 boxItemList。
 */
export function isWholeBoxRow(one: IShopCartRow) {
  return Number(one.skuId) === 0
}

/** 整盒那一行没有商品主图可取（它不挂商品），页面拿盒内第一款那张当封面 */
export function rowImageOf(one: IShopCartRow) {
  return one.mainImage ?? one.boxItemList?.[0]?.styleImage ?? null
}

/** 这一行上一共有几件实物：盒内每款件数 × 这一行是几盒（同一台盒端第二次并成一行，款式是按盒 id 现查的） */
export function boxPieceCount(one: IShopCartRow) {
  const perBox = (one.boxItemList ?? []).reduce((sum, item) => sum + Number(item.quantity ?? 0), 0)
  return perBox * Number(one.quantity ?? 1)
}

/**
 * 步进能加到几件：普通行按这一条规格的可用库存封顶，整盒那一行不封顶 ——
 * 它买的是「几盒」，某一款的仓存不是这一行的上限（库存要等下单那一刻按盒内每款逐条复核）。
 */
export function stockCapOf(one: IShopCartRow) {
  return isWholeBoxRow(one) ? Number.POSITIVE_INFINITY : (one.availableStock ?? 0)
}

export function deleteCart(ids: number[]) {
  return http.delete<void>(`/api/app/shop/cart/delete/${ids.join(',')}`)
}
