import request from '@/utils/request'

/**
 * 获取收货地址列表
 */
export function listShopAddressApi(params?: any) {
  return request({
    url: '/shop/address/list',
    method: 'get',
    params
  })
}

/**
 * 获取收货地址详情（含消费者昵称）
 */
export function detailShopAddressApi(id: any) {
  return request({
    url: '/shop/address/' + id,
    method: 'get'
  })
}

/**
 * 添加收货地址
 */
export function addShopAddressApi(data: any) {
  return request({
    url: '/shop/address/add',
    method: 'post',
    data
  })
}

/**
 * 修改收货地址
 */
export function updateShopAddressApi(data: any) {
  return request({
    url: '/shop/address/update',
    method: 'put',
    data
  })
}

/**
 * 删除收货地址
 */
export function deleteShopAddressApi(ids: number[] | number) {
  return request({
    url: `/shop/address/delete/` + ids,
    method: 'delete'
  })
}
