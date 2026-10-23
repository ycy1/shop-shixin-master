import request from '@/utils/request'

/**
 * 获取收藏列表
 */
export function listShopFavoriteApi(params?: any) {
  return request({
    url: '/shop/favorite/list',
    method: 'get',
    params
  })
}

/**
 * 获取收藏记录详情（含收藏对象名称与消费者昵称）
 */
export function detailShopFavoriteApi(id: any) {
  return request({
    url: '/shop/favorite/' + id,
    method: 'get'
  })
}

/**
 * 添加收藏记录
 */
export function addShopFavoriteApi(data: any) {
  return request({
    url: '/shop/favorite/add',
    method: 'post',
    data
  })
}

/**
 * 修改收藏记录
 */
export function updateShopFavoriteApi(data: any) {
  return request({
    url: '/shop/favorite/update',
    method: 'put',
    data
  })
}

/**
 * 删除收藏记录
 */
export function deleteShopFavoriteApi(ids: number[] | number) {
  return request({
    url: `/shop/favorite/delete/` + ids,
    method: 'delete'
  })
}
