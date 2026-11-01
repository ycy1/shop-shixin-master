import request from '@/utils/request'

/**
 * 获取票种列表（keyword 模糊匹配名称/编号，parkId/ticketType/status 精确匹配）
 */
export function listShopTicketApi(params?: any) {
  return request({
    url: '/shop/parkTicket/list',
    method: 'get',
    params
  })
}

/**
 * 获取上架中的票种选项（供「用户票种」出票表单那格下拉使用，不分页）
 * @param parkId 传了就只给这一家园区的票种（选了园区之后把候选收窄到那一家）
 */
export function optionsShopTicketApi(parkId?: any) {
  return request({
    url: '/shop/parkTicket/options',
    method: 'get',
    params: { parkId }
  })
}

/**
 * 获取票种详情
 */
export function detailShopTicketApi(id: any) {
  return request({
    url: '/shop/parkTicket/' + id,
    method: 'get'
  })
}

/**
 * 添加票种（编号由服务端生成，页面不给填）
 */
export function addShopTicketApi(data: any) {
  return request({
    url: '/shop/parkTicket/add',
    method: 'post',
    data
  })
}

/**
 * 修改票种
 */
export function updateShopTicketApi(data: any) {
  return request({
    url: '/shop/parkTicket/update',
    method: 'put',
    data
  })
}

/**
 * 删除票种（已经出出去过票的服务端会拦住）
 */
export function deleteShopTicketApi(ids: number[] | number) {
  return request({
    url: '/shop/parkTicket/delete/' + ids,
    method: 'delete'
  })
}
