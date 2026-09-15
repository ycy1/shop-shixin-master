package com.shop.wx.enums.wxpay;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum WxNotifyType {

	/**
	 * 支付通知
	 */
	NATIVE_NOTIFY("/api/wx-pay/jsapi/notify"),


	/**
	 * 退款结果通知
	 */
	REFUND_NOTIFY("/api/wx-refund/refunds/notify");

	/**
	 * 类型
	 */
	private final String type;
}
