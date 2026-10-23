import request from '@/utils/request'

/**
 * 获取订单列表（一行一张订单：sys_order 主表字段 + 名下明细的行数/件数/门店名）
 * 参数：keyword/userId/storeId/shipStatus/status/beginTime/endTime（pageNum、pageSize 分页）
 * 发货状态和门店是明细行上的列，这里的含义是"名下有这样一个商品行"
 */
export function listShopOrderApi(params?: any) {
  return request({
    url: '/shop/orderItem/order/list',
    method: 'get',
    params
  })
}

/**
 * 获取订单详情：主表字段 + items（名下全部商品明细行，含商品编码/门店名/发货仓库名）。
 * items 每行还带一格 refundingQuantity —— 这一行被在途退款单（0 待审核 / 3 退款中）
 * 占掉的件数，由服务端按各张 sys_refund.extra 现算，不是表上的列（退货弹窗按它压"还能退"）
 */
export function detailShopOrderApi(id: any) {
  return request({
    url: '/shop/orderItem/order/' + id,
    method: 'get'
  })
}

/**
 * 确认收货：把一张「已支付」的订单推成「已完成」（服务端只改 sys_order.status，
 * 明细行、库存、那张配送单都不动）。
 *
 * 为什么走 /api/comm 而不是本页其它接口的 /shop/orderItem/**：这是"两端同一个动作"的统一接口 ——
 * 买家在自己那一端点这一颗，和后台「我的订单」详情弹窗里代客点，落的必须是同一个状态
 * （ShopPayCommonController#receiveOrder）。路径上多出来的那层 /api 是前缀本身：request 的 baseURL
 * （开发环境 /api）会被 vite 代理去掉再转给后端，而后端那条路径本来就带 /api/comm，拼完正好落回去。
 *
 * 服务端还会复核一遍：消费者账号只能确认自己名下的单，而且名下每一行商品的货都已有结论（「已发货」或退了货）。
 */
export function receiveShopOrderApi(id: any) {
  return request({
    url: '/api/comm/shop/order/receive/' + id,
    method: 'post'
  })
}

/**
 * 申请退货：只落一张 0 待审核 的退款单（sys_refund），订单状态、明细行的已退件数、库存一分不动 ——
 * 那些等后台「退款管理」审核通过、退款回调成功时才做（ShopRefundCommonController#createRefund）。
 *
 * 同样走 /api/comm（两端同一个动作，双前缀的道理见上面那条）。提交的是「哪几行、各退几件」：
 *   { orderId, refundReason, items: [{ orderItemId, quantity }] }
 * **金额不在请求体里**：退款金额、退款类型（全额/部分）、单号、申请时间一律服务端按下单快照算，
 * 传了也不认（所以弹窗上那一格合计只是给人看的预览）。addressId 也没填：
 * 不传就由后端回落到这一单当初的收货地址（sys_order.address_id）。
 *
 * 服务端复核：只有「已支付 / 已完成」的单能提退、每行件数不超过「购买数量 − 已退货数量 − 别的在途退款单
 * 已占掉的件数」、退出去的钱合计不超过实付金额；消费者账号只能退自己名下的单。
 * 一张订单**可以**同时挂着好几张在途退款单（每次退的商品、件数都可以不一样），所以没有"有在途的就不给再提"这一道。
 */
export function createShopRefundApi(data: any) {
  return request({
    url: '/api/comm/shop/refund/create',
    method: 'post',
    data
  })
}

/**
 * 一张订单名下的全部退款单（列表上那颗「退款单」弹窗，一行一张退款单）。
 *
 * 退款可以一次次提：在途的那几张（0 待审核 / 3 退款中）不拦新单，只按行占各自那几件的额度，
 * 但那几张处理完（退成功 / 被拒绝 / 失败 / 取消）之后还能接着提，所以这一路是列表不是单条，
 * 按 id 正序回来（最新一张在末尾）。每行的 items 就是它退了哪几行商品、各退几件、各退多少钱 ——
 * 那份内容是建单那一刻写进 sys_refund.extra 的申请快照，商品名称/编码/规格读的时候现联明细行。
 *
 * 走本页自己的 /shop/orderItem/**（纯后台读接口，不像上面那颗按钮是两端共用的动作）。
 * 服务端只认这一单的归属：消费者账号打别人的订单会报「这张订单不属于你」。
 */
export function listShopOrderRefundApi(orderId: any) {
  return request({
    url: '/shop/orderItem/order/refund/' + orderId,
    method: 'get'
  })
}

