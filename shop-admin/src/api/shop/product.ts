import request from '@/utils/request'

/**
 * 获取商品列表
 */
export function listShopProductApi(params?: any) {
  return request({
    url: '/shop/product/list',
    method: 'get',
    params
  })
}

/**
 * 获取商品详情（只有商品本身那几格，单价/规格在 SKU 那边；价格区间、销量、可用库存、成本价是按名下 SKU 汇总的只读数）
 */
export function detailShopProductApi(id: any) {
  return request({
    url: '/shop/product/' + id,
    method: 'get'
  })
}

/**
 * 添加商品
 */
export function addShopProductApi(data: any) {
  return request({
    url: '/shop/product/add',
    method: 'post',
    data
  })
}

/**
 * 修改商品
 */
export function updateShopProductApi(data: any) {
  return request({
    url: `/shop/product/update`,
    method: 'put',
    data
  })
}

/**
 * 删除商品
 */
export function deleteShopProductApi(ids: number[] | number) {
  return request({
    url: `/shop/product/delete/` + ids,
    method: 'delete'
  })
}
