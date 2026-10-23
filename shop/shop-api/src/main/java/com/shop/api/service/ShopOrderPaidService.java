package com.shop.api.service;

import com.shop.dto.order.ShopOrderPaidDto;
import com.shop.vo.order.ShopOrderPaidVo;

/**
 * 支付结果通知 服务接口（小程序侧，付完款之后调用）
 */
public interface ShopOrderPaidService {

    /**
     * 把一张订单标成"已支付"，并按它的商品明细生成一张线上订单出库单（outbound_source 固定 1）。
     *
     * 只做这两件事：主单从 0 待支付推到 1 已支付（补 pay_time，pay_type 传了才写），
     * 出库单落 0 待出库、明细逐行把 shop_order_item 的商品/仓库/购买数量抄过来。
     * **不动库存**：下单时锁住的那份可用量原样留着，直到后台「商品出库」页点「出库」才减总库存、
     * 同时把锁定还回去（见 ShopErpSaleOutboundService#outbound）。
     *
     * 幂等：一张订单只自动建一张出库单。重复回调（支付网关会重试）拿到的是同一张单、
     * outboundCreated=false；库存也只会在真正点出库那一次被扣，所以重复回调不会多扣货。
     *
     * @param form 通知参数（orderId / orderNo 至少给一个，可选 payType、payAmount）
     * @return 订单状态 + 出库单号；订单已取消/已退款/已关闭时抛异常
     */
    ShopOrderPaidVo paid(ShopOrderPaidDto form);
}
