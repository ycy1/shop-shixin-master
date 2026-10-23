import type { IShopOrderForm, IShopOrderPaid, IShopOrderPayForm, IShopOrderPreview } from './types/shop'
import { http } from '@/http/http'

/**
 * 下单试算与统一下单走的是 /api/comm：这一颗动作后台「我的订单」那半也会碰，
 * 属于两端共用，不是 App 独有（口径见 ShopPayCommonController）。
 * 两个接口入参完全一样，金额、配送费、券抵扣全部服务端算，页面只摆不拼。
 */
export function previewOrder(form: IShopOrderForm) {
  return http.post<IShopOrderPreview>('/api/comm/shop/order/preview', form)
}

export function createOrder(form: IShopOrderForm) {
  return http.post<{ orderId: number, orderNo: string }>('/api/comm/shop/order/create', form)
}

/**
 * 待支付那一单付掉它：调的就是服务端那条支付回调（微信/支付宝网关还没接，点「确认支付」等价于把这一单记成已支付）。
 * 主单 0→1、还的锁转正、券核销、积分冻结转消耗、出库单和配送单都在那一段里走完，页面不参与任何一步。
 * 重复点付不了两次：那边按 WHERE status = 0 更新，第二趟翻不动就按「状态不允许」拒回来。
 */
export function payOrder(form: IShopOrderPayForm) {
  return http.post<IShopOrderPaid>('/api/comm/shop/order/payCallback', form)
}
