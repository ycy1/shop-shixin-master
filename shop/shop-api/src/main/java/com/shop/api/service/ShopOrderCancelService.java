package com.shop.api.service;

import com.shop.vo.order.ShopOrderCancelVo;

/**
 * 取消订单（只处理还没付钱的那一单，写在 shop-api）
 *
 * 和统一下单、下单试算、支付回调、确认收货那几路同一个入口（ShopPayCommonController，
 * 前缀 /api/comm/shop/order）：买家在自己那一端点「取消订单」，后台「我的订单」里代客点同一颗按钮，
 * 落的必须是同一套状态、还回去的必须是同一批东西，所以不能一边一份实现。
 *
 * 一次取消要一起做完的六件事（缺一个就是账对不上）：
 * 1. sys_order.status 0 待支付 → 3 已取消
 * 2. 这一单名下那条下单时就建好的配送单 → 5 已取消（见 ShopOrderDeliveryService#cancelByOrder）
 * 3. 下单时锁在各行发货仓上的那份可用量还回库存（locked_stock 减回去）
 * 4. 这一单核销掉的优惠券退回用户手上（见 ShopOrderCouponService#giveBack）
 * 5. 下单时冻住的那一笔积分整笔解冻还回可用（见 ShopOrderPointsService#unfreeze）
 * 6. 下单选的是余额支付时占住的那一笔钱整笔退回可用余额（见 ShopOrderWalletService#unfreeze）
 *
 * 只认「0 待支付」这一档：付过钱的单要退的是钱，走统一退货那条链（建退款单 + 退款回调），
 * 从这儿取消会把一张已经收了款的单悄悄标成"没成交"，货款两头都不认。
 * 积分那一份也跟着这一档走：没成交就是没花，所以这里是解冻（frozen → 可用）；
 * 付过钱再退货那种已经把分扣掉的，退款回调那头走的是另一条路（已消耗 → 可用，见 ShopOrderPointsService#giveBack）。
 * 余额那一份同理：没成交就是没花钱，所以这里是解冻（冻结余额 → 可用余额），
 * 付过钱再退货那种已经扣掉的，退款回调那头走的是另一条路（累计消费 → 可用余额，见 ShopOrderWalletService#giveBack）。
 */
public interface ShopOrderCancelService {

    /**
     * 取消一张还没支付的订单，六件事在同一个事务里做完，任何一件不成都整单回滚。
     *
     * 谁能取消哪一单判的是账号类型（口径同确认收货 ShopOrderReceiveService#receive）：
     * 消费者账号只能取消自己名下的单，其它账号（平台/门店/ERP）算代客取消。
     *
     * 状态用条件更新翻（WHERE 压着 status = 0）：买家点「取消」和支付回调同时到达时，
     * 只有一边改得动这一行 —— 谁先落谁算，另一边拿到"状态刚刚被人改过"，不会出现"款收了、单取消了"。
     *
     * @param orderId sys_order.id
     */
    ShopOrderCancelVo cancel(Long orderId);
}
