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

export function deleteCart(ids: number[]) {
  return http.delete<void>(`/api/app/shop/cart/delete/${ids.join(',')}`)
}
