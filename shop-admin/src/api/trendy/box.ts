import request from '@/utils/request'

/**
 * 获取盲盒列表
 */
export function listShopBoxApi(params?: any) {
  return request({
    url: '/shop/box/list',
    method: 'get',
    params
  })
}

/**
 * 获取盲盒详情
 */
export function detailShopBoxApi(id: any) {
  return request({
    url: '/shop/box/' + id,
    method: 'get'
  })
}

/**
 * 添加盲盒（编号由服务端生成，页面不给填）
 */
export function addShopBoxApi(data: any) {
  return request({
    url: '/shop/box/add',
    method: 'post',
    data
  })
}

/**
 * 修改盲盒
 */
export function updateShopBoxApi(data: any) {
  return request({
    url: '/shop/box/update',
    method: 'put',
    data
  })
}

/**
 * 删除盲盒（服务端连带软删它名下的款式）
 */
export function deleteShopBoxApi(ids: number[] | number) {
  return request({
    url: '/shop/box/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 分页查抽取记录（params: boxId / userId / isHidden，都可空）
 */
export function listBoxRecordsApi(params?: any) {
  return request({
    url: '/shop/box/record/list',
    method: 'get',
    params
  })
}

/**
 * 获取某只盲盒名下的款式（不分页）
 */
export function listBoxItemsApi(boxId: any) {
  return request({
    url: '/shop/box/item/list',
    method: 'get',
    params: { boxId }
  })
}

/**
 * 添加款式
 */
export function addBoxItemApi(data: any) {
  return request({
    url: '/shop/box/item/add',
    method: 'post',
    data
  })
}

/**
 * 修改款式
 */
export function updateBoxItemApi(data: any) {
  return request({
    url: '/shop/box/item/update',
    method: 'put',
    data
  })
}

/**
 * 删除款式
 */
export function deleteBoxItemApi(ids: number[] | number) {
  return request({
    url: '/shop/box/item/delete/' + ids,
    method: 'delete'
  })
}
