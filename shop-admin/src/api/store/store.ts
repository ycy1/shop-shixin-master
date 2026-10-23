import request from '@/utils/request'

/**
 * 获取门店列表
 */
export function listShopStoreApi(params?: any) {
  return request({
    url: '/shop/store/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的门店选项（不分页，供商品表单下拉使用）
 */
export function optionsShopStoreApi() {
  return request({
    url: '/shop/store/options',
    method: 'get'
  })
}

/**
 * 获取当前登录用户担任店长的门店（数组；不是门店用户时为空数组）
 */
export function myShopStoresApi() {
  return request({
    url: '/shop/store/my',
    method: 'get'
  })
}

/**
 * 获取门店详情
 */
export function detailShopStoreApi(id: any) {
  return request({
    url: '/shop/store/' + id,
    method: 'get'
  })
}

/**
 * 添加门店
 */
export function addShopStoreApi(data: any) {
  return request({
    url: '/shop/store/add',
    method: 'post',
    data
  })
}

/**
 * 修改门店
 */
export function updateShopStoreApi(data: any) {
  return request({
    url: '/shop/store/update',
    method: 'put',
    data
  })
}

/**
 * 删除门店
 */
export function deleteShopStoreApi(ids: number[] | number) {
  return request({
    url: '/shop/store/delete/' + ids,
    method: 'delete'
  })
}
