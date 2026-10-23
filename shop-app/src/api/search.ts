import type { IShopBrand, IShopStore } from './types/shop'
import { http } from '@/http/http'

/**
 * 门店 / 品牌详情（App 端读）。
 *
 * 路径统一在 /api/app 下，控制器只是转一手，service 仍是 Web 端那一份 ShopSearchReadService：
 * 门店页和品牌页两端要看的必须是同一家店、同一个品牌，判据（只看启用中的、封面图只在详情那条给）
 * 全在服务层里那一个方法，两端各指各的路径也不会分叉，分包口径同时保持干净。
 *
 * 三条都不要求登录：SaTokenConfigure 把 /api/** 整体排在登录拦截之外，游客也能逛店逛品牌。
 */
export function getStoreDetail(id: number) {
  return http.get<IShopStore>(`/api/app/shop/store/${id}`)
}

export function getBrandDetail(id: number) {
  return http.get<IShopBrand>(`/api/app/shop/brand/${id}`)
}

/** 品牌名下的门店：后端不接分页（一个品牌就那么几家），整排给回 */
export function getBrandStores(id: number) {
  return http.get<IShopStore[]>(`/api/app/shop/brand/${id}/stores`)
}
