import request from '@/utils/request'

/**
 * 获取徽章列表（联表带出目标名称：类型是门店/活动时联门店/活动表取名，签到/消费两类没有目标、那一格是 NULL；
 * 已获得人数按用户徽章表现数出来，库里没这一列）
 */
export function listShopBadgeApi(params?: any) {
  return request({
    url: '/activity/badge/list',
    method: 'get',
    params
  })
}

/**
 * 获取一条徽章详情（修改弹窗和只读详情弹窗共用）
 */
export function detailShopBadgeApi(id: any) {
  return request({
    url: '/activity/badge/' + id,
    method: 'get'
  })
}

/**
 * 获取一枚徽章的获取记录（列表页那颗「获取记录」弹窗，按徽章分页、最新一条在前；
 * 这一页只有三条写口子：删除、改「状态」、改「是否展示」，见下面三个）
 * params: { badgeId, source（1系统发放 2活动获得 3消费达标 4手动发放 5门店发放）,
 *           status（1有效 0失效）, keyword（持有人昵称/手机号）, pageNum, pageSize }
 */
export function listShopBadgeRecordApi(params?: any) {
  return request({
    url: '/activity/badge/record/list',
    method: 'get',
    params
  })
}

/**
 * 删除获取记录：把一枚徽章从某个人名下抹掉（「获取记录」弹窗里的「删除」和「批量删除」）
 * ids 是明细行的 id（一行就是"一人一枚"），不是用户 id —— 一个人可以有几枚徽章
 * 服务端只删这一张表上那一行，当初随徽章一起兑现的积分和券不收回（收分走积分页、作废券走优惠券页）
 */
export function deleteShopBadgeRecordApi(ids: number[] | number) {
  return request({
    url: '/activity/badge/record/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 改一条获取记录的「状态」（弹窗里那一格的开关，一改就提交）
 * 只传 id（明细行 id）和 status（1 有效 / 0 失效），服务端也只改这一列，不动快照、有效期和是否展示
 * 失效不收回积分和券、也不删这一行；前台/APP 读这一格判「这一枚还算不算数」（失效的变灰）
 */
export function updateShopBadgeRecordStatusApi(id: number, status: number) {
  return request({
    url: '/activity/badge/record/status',
    method: 'put',
    data: { id, status }
  })
}

/**
 * 改一条获取记录的「是否展示」（弹窗里那一格的开关，一改就提交）
 * 只传 id（明细行 id）和 isShow（1 展示 / 0 隐藏），服务端也只改这一列，不动快照、有效期和状态
 */
export function updateShopBadgeRecordShowApi(id: number, isShow: number) {
  return request({
    url: '/activity/badge/record/show',
    method: 'put',
    data: { id, isShow }
  })
}

/**
 * 新增徽章（状态不传按 1 启用；签到/消费两类的目标由服务端清成 NULL；达标线留 0 就是只走手动下发）
 */
export function addShopBadgeApi(data: any) {
  return request({
    url: '/activity/badge/add',
    method: 'post',
    data
  })
}

/**
 * 修改徽章（状态整行提交；可空的那几格不传就清掉。改动只往后生效，已发出去的各有三格快照）
 */
export function updateShopBadgeApi(data: any) {
  return request({
    url: '/activity/badge/update',
    method: 'put',
    data
  })
}

/**
 * 删除徽章（只删还没发出去过的那几枚，这条判据在服务端）
 */
export function deleteShopBadgeApi(ids: number[] | number) {
  return request({
    url: '/activity/badge/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 下发徽章：把一枚徽章手工发给选中的每个用户（不看达标线，已经持有这一枚的自动跳过）
 * data: { badgeId, userIds: number[], endTime }，endTime 不传就是永久有效
 * 积分和券那两格奖励由后端按定义表当场兑现，参数里不收；返回实发人数
 */
export function giveShopBadgeApi(data: any) {
  return request({
    url: '/activity/badge/give',
    method: 'post',
    data
  })
}
