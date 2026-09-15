package com.shop.wx.enums.wxpay;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
/**
 * 微信平台订单状态
 */
public enum WxTradeState {

    /**
     * 支付成功
     */
    SUCCESS("SUCCESS"),

    /**
     * 未支付
     */
    NOTPAY("NOTPAY"),

    /**
     * 已关闭
     */
    CLOSED("CLOSED"),

    /**
     * 转入退款
     */
    REFUND("REFUND");

    /**
     * 类型
     */
    private final String type;
}
