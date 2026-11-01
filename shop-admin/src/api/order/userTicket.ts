import request from '@/utils/request'

/**
 * 获取用户票列表
 *
 * 参数：keyword（票码/票种名/持票人姓名电话）/ userId / parkId / ticketId / ticketType / status /
 * beginVisitDate、endVisitDate（游玩日期区间，含两端）
 * 消费者账号进来时服务端把 userId 钉成本人，所以「消费者管理 → 我的订单」的票种 tab 也走这一条
 */
export function listUserTicketApi(params?: any) {
  return request({
    url: '/shop/userTicket/list',
    method: 'get',
    params
  })
}

/**
 * 获取一张票的详情
 */
export function detailUserTicketApi(id: any) {
  return request({
    url: '/shop/userTicket/' + id,
    method: 'get'
  })
}

/**
 * 代客出票：票码由服务端生成；parkId 和票面那几格（票名/类型/票价）由服务端按票种行带成快照，
 * 前端传了也不认 —— 表单只需要挑「给谁、哪张票种、哪天来玩」
 */
export function addUserTicketApi(data: any) {
  return request({
    url: '/shop/userTicket/add',
    method: 'post',
    data
  })
}

/**
 * 修改一张票：服务端只放开游玩日期、入园时段、持票人三格和备注，
 * 而且只有「0 未使用」的票改得动（核销过/退过的票要改只能删了重出）
 */
export function updateUserTicketApi(data: any) {
  return request({
    url: '/shop/userTicket/update',
    method: 'put',
    data
  })
}

/**
 * 入园核销：0 未使用 → 1 已核销，服务端同时盖 used_time。
 * 后台页面上没有这颗按钮（他 2026-10-28 说「不需要入园核销」，核销是闸机/扫码那侧的动作），
 * 端点和 shop:userTicket:verify 那行权限都留着，接扫码那侧时直接用；这一条先当接口绑定留在这儿。
 */
export function verifyUserTicketApi(id: any) {
  return request({
    url: '/shop/userTicket/verify/' + id,
    method: 'put'
  })
}

/**
 * 退款：0 未使用 / 1 已核销 → 3 已退款，服务端同时盖 refund_time。
 * 只改状态、不动钱 —— 这一期没有购票支付链路，真接上统一下单时这一条要改成走退款单
 */
export function refundUserTicketApi(id: any) {
  return request({
    url: '/shop/userTicket/refund/' + id,
    method: 'put'
  })
}

/**
 * 重出这张票的票面二维码：票码不变，只按同一个票码重新生成一张 PNG、重新上传、覆盖 qr_img。
 * 出票那一步的出图是"失败只记日志、不回滚"，所以 qr_img 有可能是空（列表那一格是 '-'），
 * 这颗按钮就是给补那一张图用的；已经图上再点只是换一张新的，扫出来还是同一串票码。
 * 权限走的是这一行已有的 shop:userTicket:update（没另开一档按钮权限）。
 */
export function refreshUserTicketQrApi(id: any) {
  return request({
    url: '/shop/userTicket/qr/' + id,
    method: 'put'
  })
}

/**
 * 删除用户票（逻辑删除）
 */
export function deleteUserTicketApi(ids: number[] | number) {
  return request({
    url: '/shop/userTicket/delete/' + ids,
    method: 'delete'
  })
}
