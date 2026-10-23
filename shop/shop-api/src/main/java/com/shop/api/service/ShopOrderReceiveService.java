package com.shop.api.service;

import com.shop.vo.order.ShopOrderReceiveVo;

/**
 * 订单确认收货 服务接口（统一入口：买家自己那一端点，和后台「我的订单」详情弹窗里代客点，是同一件事）
 *
 * 写在 shop-api 而不是后台那套 service 里的理由和统一下单、统一接单一样：买家端摸不到 shop-admin 的
 * service（shop-api 只依赖 shop-commom），两边各写一套的话，"已完成"这一档就会有两种说法。
 */
public interface ShopOrderReceiveService {

    /**
     * 买家收到货了：把一张「1 已支付」的订单推成「2 已完成」。
     *
     * 判据只有一条 —— **名下每一行商品的货都已有结论**：`ship_status=1 已发货`（那一档由统一接单写：
     * 货交到送货员手上那一刻才算发出去，见 ShopDeliveryAcceptService#accept），或者已经退了货
     * （4 部分退货 / 2 已退货 —— 2026-09-22 他点头放行：退了货的那一行不会再补发货，卡着等于让整单
     * 永远收不了尾）。还停在 0 待发货 / 3 已出库 的行是"这一单没发完"，照样拒，不替人猜。
     * 整单退满的那一张不受影响 —— 退款回调已把它推成 4 已退款，前面"只有已支付能收"那道状态闸先拦下。
     *
     * 只改 sys_order.status 这一格：不动明细行、不碰库存、也不去动那张配送单 ——
     * 配送单走到 4 已送达 是送货员那一步的事，两边各记各的，谁也不替谁收尾。
     *
     * @param orderId 哪一张订单（sys_order.id）
     * @return 收货后的那几格（状态、时间、行数）
     */
    ShopOrderReceiveVo receive(Long orderId);
}
