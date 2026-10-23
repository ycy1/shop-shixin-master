package com.shop.api.service;

import com.shop.dto.refund.ShopRefundCreateDto;
import com.shop.vo.refund.ShopRefundCreateVo;

/**
 * 统一退货 服务接口
 *
 * 和统一下单、支付回调同一个位置（shop-api）的理由：两端点的是同一颗按钮 ——
 * 买家在自己那一端提退货、和后台「我的订单」列表上代客点「退货」，落的必须是同一张 sys_refund、
 * 走的是同一套校验（能退哪几行、各退几件、这一行还能退多少钱）。写在后台那套 service 里，
 * 买家端就摸不到（shop-api 只依赖 shop-commom），两边各写一套必然分叉。
 */
public interface ShopRefundCreateService {

    /**
     * 按"退哪几行、各退几件"建一张待审核的退款单（sys_refund.status=0）。
     *
     * 只做这一件事：不改订单状态、不改明细行的已退数量、不碰库存 —— 那些都在退款回调成功那一刻才做。
     * 金额由服务端按明细的成交金额和小计算，退款类型（全额/部分）按"这次退完是不是所有行都退满"判。
     *
     * @param form 哪张订单、退哪几行各几件、寄回哪个地址、为什么退
     * @return 建出来的那张退款单（单号、金额、状态、行数件数）
     */
    ShopRefundCreateVo create(ShopRefundCreateDto form);
}
