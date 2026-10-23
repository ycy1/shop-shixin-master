package com.shop.api.service;

import com.shop.dto.refund.ShopRefundCallbackDto;
import com.shop.vo.refund.ShopRefundCallbackVo;

/**
 * 退款结果通知 服务接口
 *
 * 位置和理由同统一退货、统一下单那两条：这一条是"钱真退出去了"那一刻的记账动作，
 * 将来渠道（微信/支付宝）的回调、和后台人工确认"钱已退"走的是同一套回写 ——
 * 明细行的已退数量、订单的已退款状态、退货入库单，哪一头都不能只在一个入口做。
 */
public interface ShopRefundCallbackService {

    /**
     * 把一张已审核通过的退款单翻成终态。
     *
     * 成功（默认）：sys_refund 3 退款中（审核通过那一刻）→ 4 退款成功（补 refund_time、落 third_refund_no），
     * 按建单时落在 sys_refund.extra 的那份行快照回写 shop_order_item.returned_quantity
     * （退满的行顺带 ship_status → 2 已退货，只退了一部分的行 → 4 部分退货），整单退满才把 sys_order.status 推成 4 已退款；
     * 最后按发货仓一家一张建 inbound_source=1 线上退货 的入库单（状态 0 待入库，
     * 库存要等仓库在「商品入库」页点「入库」才真加，口径和出库侧对称）；
     * 整单退满（主单这次被推成 4）的那一刻，把核销在这一单上的优惠券退回用户，部分退款不返；
     * 积分那一头不等整单退满 —— 这一批退掉的货对应多少分就还多少分，同时把当初为这批货送出去的分收回来
     * （一退一收两笔，按行按件数比例，见 ShopOrderPointsService#giveBack）。
     * 失败：只把 3 → 5 退款失败并记下 fail_reason，明细、库存和积分一分不动。
     *
     * @param form 哪张退款单、成功没有、渠道那边的单号
     * @return 处理结果（退款单终态、回写了几行几件、返了几张券、退了几分、建出来的那几张退货入库单）
     */
    ShopRefundCallbackVo callback(ShopRefundCallbackDto form);
}
