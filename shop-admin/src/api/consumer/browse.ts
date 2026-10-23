import request from '@/utils/request'

/**
 * 获取浏览足迹列表
 */
export function listShopBrowseApi(params?: any) {
  return request({
    url: '/shop/browse/list',
    method: 'get',
    params
  })
}

/**
 * 获取浏览足迹详情（含商品名称/单价与消费者昵称）
 */
export function detailShopBrowseApi(id: any) {
  return request({
    url: '/shop/browse/' + id,
    method: 'get'
  })
}

/**
 * 记录浏览足迹（同一消费者同一商品只留一条，重复提交只刷新浏览时间）
 */
export function addShopBrowseApi(data: any) {
  return request({
    url: '/shop/browse/add',
    method: 'post',
    data
  })
}

/**
 * 修改浏览足迹
 */
export function updateShopBrowseApi(data: any) {
  return request({
    url: '/shop/browse/update',
    method: 'put',
    data
  })
}

/**
 * 删除浏览足迹
 */
export function deleteShopBrowseApi(ids: number[] | number) {
  return request({
    url: `/shop/browse/delete/` + ids,
    method: 'delete'
  })
}
