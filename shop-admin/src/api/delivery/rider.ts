import request from '@/utils/request'

/**
 * 获取送货员列表
 */
export function listShopRiderApi(params?: any) {
  return request({
    url: '/shop/rider/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的送货员选项（不分页，供配送单表单下拉使用）
 */
export function optionsShopRiderApi() {
  return request({
    url: '/shop/rider/options',
    method: 'get'
  })
}

/**
 * 获取当前登录账号自己的送货员档案（未建档时 data 为空）
 */
export function myShopRiderApi() {
  return request({
    url: '/shop/rider/my',
    method: 'get'
  })
}

/**
 * 修改自己的送货员信息（服务端只认 ShopDeliveryRiderServiceImpl#updateMyInfo 里那份字段白名单）
 */
export function updateMyShopRiderApi(data: any) {
  return request({
    url: '/shop/rider/my',
    method: 'put',
    data
  })
}

/**
 * 获取送货员详情
 */
export function detailShopRiderApi(id: any) {
  return request({
    url: '/shop/rider/' + id,
    method: 'get'
  })
}

/**
 * 添加送货员
 */
export function addShopRiderApi(data: any) {
  return request({
    url: '/shop/rider/add',
    method: 'post',
    data
  })
}

/**
 * 修改送货员
 */
export function updateShopRiderApi(data: any) {
  return request({
    url: '/shop/rider/update',
    method: 'put',
    data
  })
}

/**
 * 删除送货员
 */
export function deleteShopRiderApi(ids: number[] | number) {
  return request({
    url: '/shop/rider/delete/' + ids,
    method: 'delete'
  })
}
