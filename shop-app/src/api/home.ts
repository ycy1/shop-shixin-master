import type { IPageRes, IShopActivity, IShopBrand, IShopCategory, IShopStore } from './types/shop'
import { http } from '@/http/http'

/**
 * 首页四块（App 端读接口）。
 * 判据全在 ShopSearchReadService 里，和 Web 前台那三条同源 —— 两端看到的必须是同一批活动/门店/品牌。
 */
export function getHomeActivities() {
  return http.get<IShopActivity[]>('/api/app/shop/home/activities')
}

/** 首页那一行只铺 4 条，pageSize 给小；搜索页也读这两条，多带一个 keyword 就是搜索 */
export function getStoreList(query: { keyword?: string, pageNum?: number, pageSize?: number }) {
  return http.get<IPageRes<IShopStore>>('/api/app/shop/home/stores', query)
}

export function getBrandList(query: { keyword?: string, pageNum?: number, pageSize?: number }) {
  return http.get<IPageRes<IShopBrand>>('/api/app/shop/home/brands', query)
}

/**
 * 分类候选（首页那行筛选栏用）：一次给全、不分页，只含启用中的。
 * 服务端已按「父在前、自己的下级紧跟其后」铺平并带上 level，缩进/前缀由页面做；
 * 商品列表吃其中任何一级的 id，命中本级连同整棵下级都由服务端展开（和 Web 首页同一个口径）。
 */
export function getCategoryList() {
  return http.get<IShopCategory[]>('/api/app/shop/home/categories')
}
