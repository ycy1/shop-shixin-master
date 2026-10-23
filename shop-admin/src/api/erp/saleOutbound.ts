import request from '@/utils/request'

/**
 * 获取出库单列表（不带明细）
 * 查询条件：keyword（出库单号/订单号/备注/下单人/操作人）、orderId、outboundSource、status、
 * userId、beginTime/endTime（创建时间范围）
 */
export function listErpSaleOutboundApi(params?: any) {
  return request({
    url: '/erp/saleOutbound/list',
    method: 'get',
    params
  })
}

/**
 * 获取出库单详情（带 items 明细，明细含规格文本和该仓可用库存，后者只当出库上限提示用）
 */
export function detailErpSaleOutboundApi(id: any) {
  return request({
    url: '/erp/saleOutbound/' + id,
    method: 'get'
  })
}

/**
 * 删除出库单（明细跟着一起删）。两道拦：来源=1 线上购买一律不给删（那是买家已付款订单的发货凭证），
 * 其它来源只要有一行出过货也不给删
 */
export function deleteErpSaleOutboundApi(ids: number[] | number) {
  return request({
    url: `/erp/saleOutbound/delete/` + ids,
    method: 'delete'
  })
}

/**
 * 执行出库：只认 id 和 items 里每行的 { id, thisQuantity(本次出库数), costPrice(成本价) }。
 * 后端会按行扣该仓库存、归还下单时锁定的可用量、记库存流水，把成本价结转到行上算小计，
 * 再按进度推这张单的状态（0待出库 → 1部分出库 → 2已出库），并把货出满的那几行订单明细标成「已出库」
 * （shop_order_item.ship_status 0 → 3；订单主表没有"已出库/已发货"这两档，出到哪一步按行看）。
 * 来源=1（线上订单）时 costPrice 传了也不认：成本价和小计在支付回调建单时就按订单明细的实付金额写死了，
 * 出库只登记数量，两个数都不动。
 * 没提交的行这次不出，剩下的量下次还能继续出（所以一张单可以出好几次）
 */
export function outboundErpSaleOutboundApi(data: any) {
  return request({
    url: '/erp/saleOutbound/outbound',
    method: 'put',
    data
  })
}

/**
 * 一张销售单名下的全部出库单（只看 来源=2 销售出库 那一路），每张连明细（items）一起返回。
 * 「销售单」页那颗「出库单」弹窗用它：列表接口不带明细，逐张再调详情就是 N+1
 */
export function listErpSaleOutboundBySaleApi(saleId: any) {
  return request({
    url: '/erp/saleOutbound/bySale/' + saleId,
    method: 'get'
  })
}

/**
 * 「新建出库单」弹窗里选完销售单要带出的明细：一行一条销售明细，联表带回商品名称/编码，
 * 每行带 quantity（这一行的销售量）和 availableQuantity（还能出 = 这个商品的销售量 −
 * 这张销售单名下其它 来源=2 出库单已经登记的量，同一商品占几行时按行序分摊）。
 * 额度是那张销售单自己的数，刻意不按登录账号收窄（否则两个操作员各建一张就能把同一批货出两遍）
 */
export function saleItemsErpSaleOutboundApi(saleId: any) {
  return request({
    url: '/erp/saleOutbound/saleItems/' + saleId,
    method: 'get'
  })
}

/**
 * 新建出库单：来源给四档（默认 2 销售出库），1 线上购买由支付回调按订单明细自动建、
 * 3 采购退货由采购单页那颗「采购退货」建，那两档后端 insert 一样挡掉。
 * data 只认 outboundSource + orderId（只有 2 那一路要，指向销售单）+ items 每行的
 * (productId, warehouseId, quantity, costPrice) + remark，
 * 单号、order_no、总数、总金额、状态一律后端算；2 那一路成本价不填就取那行商品的销售单价，
 * 4 调拨出库 / 5 报损出库 / 6 其他 没有来源单据（order_id/order_no 落 NULL）、没有额度这道闸，成本价必填。
 * 建出来的是 0 待出库 的单子，货要在这页点「出库」才真离仓、才减库存
 */
export function addErpSaleOutboundApi(data: any) {
  return request({
    url: '/erp/saleOutbound/add',
    method: 'post',
    data
  })
}

/**
 * 「确认」：把这张出库单绑着的那一条配送单从 0 待派单 推成 1 待接单，推进送货员的抢单池。
 *
 * 这一颗按钮不再建单了：配送单在**统一下单**那一刻就建好了（重量 = Σ(商品重量 × 数量)、发货点 = 分到的那家仓、
 * 距离和配送费也在那一次定死，买家付的钱里就含着它），支付回调只是把它换绑到这张出库单上。
 * 所以这里只翻状态，配送单上的钱和货的那几格一概不动，也没有弹窗。
 *
 * 判据全在后端（这张单出满没有、绑没绑上配送单、那条还是不是 0 待派单），不满足就报错回来；
 * 权限和「配送单」那颗按钮同一颗：erp:saleOutbound:delivery
 */
export function confirmDeliveryErpSaleOutboundApi(id: any) {
  return request({
    url: `/erp/saleOutbound/confirmDelivery/` + id,
    method: 'put'
  })
}
