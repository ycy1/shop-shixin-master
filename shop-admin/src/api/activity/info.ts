import request from '@/utils/request'

/**
 * 获取活动列表（关键字搜名称/标题，联表带出门店名，曝光量和点击量按流水表现算、点击率由两者相除）
 */
export function listShopActivityApi(params?: any) {
  return request({
    url: '/activity/list',
    method: 'get',
    params
  })
}

/**
 * 获取一条活动的曝光/点击明细（列表页那颗「查看记录」弹窗，只读、分页）
 * params: { activityId 必填, eventType 可选（1曝光 2点击）, pageNum, pageSize }
 */
export function listShopActivityRecordApi(params?: any) {
  return request({
    url: '/activity/record/list',
    method: 'get',
    params
  })
}

/**
 * 获取活动详情
 */
export function detailShopActivityApi(id: any) {
  return request({
    url: '/activity/' + id,
    method: 'get'
  })
}

/**
 * 新增活动（状态一律落 1 未开始；曝光量/点击量/点击率三格由服务端定，传了也不认）
 */
export function addShopActivityApi(data: any) {
  return request({
    url: '/activity/add',
    method: 'post',
    data
  })
}

/**
 * 修改活动（状态也在这一格改；第一次进 2 投放中时服务端盖投放时间，之后不覆盖）
 */
export function updateShopActivityApi(data: any) {
  return request({
    url: '/activity/update',
    method: 'put',
    data
  })
}

/**
 * 删除活动（只有从没投放过的能删，这条判据在服务端）
 */
export function deleteShopActivityApi(ids: number[] | number) {
  return request({
    url: '/activity/delete/' + ids,
    method: 'delete'
  })
}
