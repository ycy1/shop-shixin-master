import request from '@/utils/request'

/**
 * 获取购物车列表
 */
export function listShopCartApi(params?: any) {
  return request({
    url: '/shop/cart/list',
    method: 'get',
    params
  })
}

/**
 * 获取购物车记录详情（含商品名称、主图、单价与消费者昵称）
 */
export function detailShopCartApi(id: any) {
  return request({
    url: '/shop/cart/' + id,
    method: 'get'
  })
}

/**
 * 添加购物车记录
 */
export function addShopCartApi(data: any) {
  return request({
    url: '/shop/cart/add',
    method: 'post',
    data
  })
}

/**
 * 修改购物车记录
 */
export function updateShopCartApi(data: any) {
  return request({
    url: '/shop/cart/update',
    method: 'put',
    data
  })
}

/**
 * 删除购物车记录
 */
export function deleteShopCartApi(ids: number[] | number) {
  return request({
    url: `/shop/cart/delete/` + ids,
    method: 'delete'
  })
}
