import request from '@/utils/request'

/**
 * 获取商品SKU列表（分页）
 */
export function listShopProductSkuApi(params?: any) {
  return request({
    url: '/shop/productSku/list',
    method: 'get',
    params
  })
}

/**
 * 获取某个商品下的全部SKU（不分页，商品页那颗「SKU管理」按钮用的）
 */
export function skusByProductApi(productId: any) {
  return request({
    url: '/shop/productSku/byProduct/' + productId,
    method: 'get'
  })
}

/**
 * 添加商品SKU
 */
export function addShopProductSkuApi(data: any) {
  return request({
    url: '/shop/productSku/add',
    method: 'post',
    data
  })
}

/**
 * 修改商品SKU
 */
export function updateShopProductSkuApi(data: any) {
  return request({
    url: `/shop/productSku/update`,
    method: 'put',
    data
  })
}

/**
 * 删除商品SKU
 */
export function deleteShopProductSkuApi(ids: number[] | number) {
  return request({
    url: `/shop/productSku/delete/` + ids,
    method: 'delete'
  })
}
