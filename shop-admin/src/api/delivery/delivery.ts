import request from '@/utils/request'

/**
 * 获取配送单列表
 */
export function listShopDeliveryApi(params?: any) {
  return request({
    url: '/shop/delivery/list',
    method: 'get',
    params
  })
}

/**
 * 获取配送单详情
 */
export function detailShopDeliveryApi(id: any) {
  return request({
    url: '/shop/delivery/' + id,
    method: 'get'
  })
}

/**
 * 添加配送单
 */
export function addShopDeliveryApi(data: any) {
  return request({
    url: '/shop/delivery/add',
    method: 'post',
    data
  })
}

/**
 * 修改配送单
 */
export function updateShopDeliveryApi(data: any) {
  return request({
    url: '/shop/delivery/update',
    method: 'put',
    data
  })
}

/**
 * 删除配送单
 */
export function deleteShopDeliveryApi(ids: number[] | number) {
  return request({
    url: '/shop/delivery/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 接单：把某一单揽到送货员名下（后端顺带按他的交通工具估出预计时长/预计送达，
 * 并把这一单里已出库的那几行订单明细标成「已发货」）。
 *
 * 为什么走 /api/comm 而不是本页其它接口的 /shop/delivery/**：这是"两端同一个动作"的统一接口，
 * 送货员端和后台点的是同一条（ShopRiderCommonController#acceptDelivery），两边各写一套状态就会分叉。
 * 路径上多出来的那层 /api 是前缀本身：request 的 baseURL（开发环境 /api）会被 vite 代理去掉再转给后端，
 * 而后端这个控制器的真实路径就带 /api/comm，所以这里要写成 /api/comm/...，拼完正好落回后端那一条。
 *
 * data：{ deliveryId, riderId }。送货员账号那一档 riderId 不用传（后端按他自己的档案定，传了也不认）；
 * 非送货员账号（后台）调这一条必须点名是哪一位 —— 配送管理列表上那颗按钮因此只给送货员本人账号看
 * （见那个页面的 isRiderAccount），后台要派单走「修改」里那位送货员那一格。
 */
export function acceptShopDeliveryApi(data: any) {
  return request({
    url: '/api/comm/shop/delivery/accept',
    method: 'post',
    data
  })
}

/**
 * 取货：货拿到手、这一单上路（2 已接单 → 3 配送中，把 pickup_time 记上）。
 * 送达：货交到收件人手上（3 配送中 → 4 已送达，记 delivered_time）。
 *
 * 和接单同一条统一接口那一路（ShopRiderCommonController#pickupDelivery / #deliveredDelivery）：送货员端点的也是这两颗，
 * 两边各写一套状态就会分叉。入参只有 { deliveryId } —— **没有 riderId**，这一单派给谁就是谁在跑，
 * 再收一次 riderId 等于给后门（送货员账号只能动 rider_id 是自己那一份的单，后台账号是替单上那位记这一步，
 * 所以这一颗按钮后台账号也给看，不像接单那样只挂在送货员本人身上）。
 *
 * 状态机严格一格一跳：没取货就点送达会被后端拒（"只有「配送中」的配送单能送达"），
 * 所以按钮按 status 分档出现（2 才给取货、3 才给送达），不跨档补记。
 * 送达不回写 sys_order：那一单收没收货走的是买家侧的「确认收货」（/api/comm/shop/order/receive/{id}）。
 */
export function pickupShopDeliveryApi(data: any) {
  return request({
    url: '/api/comm/shop/delivery/pickup',
    method: 'post',
    data
  })
}

export function deliveredShopDeliveryApi(data: any) {
  return request({
    url: '/api/comm/shop/delivery/delivered',
    method: 'post',
    data
  })
}

/**
 * 取消：这一单不送了（0 待派单 / 1 待接单 / 2 已接单 / 3 配送中 → 5 已取消，记 cancel_time + cancel_reason）。
 *
 * 和上面三颗同一条统一接口那一路（ShopRiderCommonController#cancelDelivery），入参也只要 { deliveryId, cancelReason }
 * —— 没有 riderId，理由同上；cancelReason 选填，不填后端兜一句「配送单人工取消」。这一路和取货/送达有两处不同：
 *   1. 不要求这一单已经有人接（0/1 那两条还没派出去的也取消得动），所以送货员账号只有"派给自己那一份"能取消，
 *      抢单池里还没人的单得到「配送管理」这页来点（后端会明确拒）；
 *   2. 它不止改配送单：还顺带把这一单名下「1 已发货」的订单明细行退回「3 已出库」（货没送到，那一行还得能再发），
 *      退回几行在返回的 revertedItemCount 里，取货/送达那两路这一格是 null。
 *
 * 已送达那一档（4）取消不了，要退货去订单那边提退货；订单自己被取消/关闭带下来的那一路也不在这里
 * （那是 ShopOrderDeliveryServiceImpl#cancelByOrder，只收 0/1，而且把派单痕迹一并清掉；这一路是人在按钮前
 * 点"这一单不送了"，rider_id 和各步时间都留着，要查是谁跑到哪一步的）。
 */
export function cancelShopDeliveryApi(data: any) {
  return request({
    url: '/api/comm/shop/delivery/cancel',
    method: 'post',
    data
  })
}
