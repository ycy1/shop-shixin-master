import request from '@/utils/request'

/**
 * 获取积分账户列表（联表带出昵称和手机号；五个数字读的都是库里存的那几格，可用积分多的排前面）
 * params: { keyword 可选（昵称/账号/手机号）, userId 可选, pageNum, pageSize }
 */
export function listShopPointsApi(params?: any) {
  return request({
    url: '/activity/points/list',
    method: 'get',
    params
  })
}

/**
 * 获取某个用户的积分流水（列表页那颗「积分流水」弹窗，按人分页、最新一笔在前，只读）
 * params: { userId 必填, bizType 可选（1签到 2购物 3评价 4分享 5邀请 6注册 7兑换 8抵扣 9过期 10管理员调整）, pageNum, pageSize }
 * points 是带符号的（获取为正、消耗为负），balance 记的是这一笔之后的余额
 */
export function listShopPointsRecordApi(params?: any) {
  return request({
    url: '/activity/points/record/list',
    method: 'get',
    params
  })
}

/**
 * 调整用户积分（列表页那颗「修改」）：收 userId / type（1 增加 2 减少）/ points（多少个）/ reason（为什么），
 * 增加那一头还可以带 expireTime（这批分几时过期，选填，落在流水那一条的过期时间上），
 * 那五个账面数字一概不在参数里 —— 加和减都是带闸门的原子 UPDATE（扣超了直接拒），
 * 而且改账和写「10 管理员调整」那一笔流水在同一个事务里
 * data: { userId, type, points, reason, expireTime }，返回调整后这个用户的可用积分
 */
export function adjustShopPointsApi(data: any) {
  return request({
    url: '/activity/points/adjust',
    method: 'post',
    data
  })
}
