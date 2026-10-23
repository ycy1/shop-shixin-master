import type { IPageRes, IShopProduct, IShopProductDetail, IShopProductQuery } from './types/shop'
import { http } from '@/http/http'

/**
 * 商品（App 端读接口）。
 * 前缀 /api/app 由后端 WebMvcConfig 按 BaseAppController 加上，所以这里要写全路径 ——
 * VITE_SERVER_BASEURL 只到主机（http://127.0.0.1:8800），不带 /api。
 */
export function getProductList(query: IShopProductQuery) {
  return http.get<IPageRes<IShopProduct>>('/api/app/shop/product/list', query)
}

/** 商品详情：下架/删掉的行服务端直接报错，页面按“商品不存在”处理 */
export function getProductDetail(id: number) {
  return http.get<IShopProductDetail>(`/api/app/shop/product/${id}`)
}
