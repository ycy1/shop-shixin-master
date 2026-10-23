package com.shop.dto.order;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付结果通知参数（订单已付成功，服务端据此把主单置为已支付并按订单明细生成一张出库单）
 *
 * 只收"哪张单付了、用什么付的、付了多少"这三件事：
 * 支付时间由服务端取当前时间（客户端和网关的时间戳谁都可以编，记了也不可信），
 * 出库单的明细行、仓库、计划数量一律由服务端按 shop_order_item 抄，参数里传了也不认。
 *
 * 注意：这个端点在 /api/** 白名单里（不走 Sa-Token），所以调用方必须自己保证通知是真的
 * —— 微信回调那一路要先在 WxPayServiceImpl#jsapiNotify 验签通过后再调本接口（见服务实现类注释）。
 */
@Data
@ApiModel(value = "支付回调参数")
public class ShopOrderPaidDto {

    @ApiModelProperty(value = "订单ID(sys_order.id)，和 orderNo 至少给一个；两个都给以 orderId 为准")
    private Long orderId;

    @ApiModelProperty(value = "订单号(sys_order.order_no)，没给 orderId 时按它找单")
    private String orderNo;

    @ApiModelProperty(value = "支付方式：1微信 2支付宝 3银行卡 4余额（字典 pay_type），不传则不写这一列")
    private Integer payType;

    @ApiModelProperty(value = "实付金额（分单位换算后的元）。传了就和服务端算出来的 sys_order.pay_amount 比对，"
            + "对不上直接拒；不传跳过校验（后台补单那类调用拿不到网关金额时用）")
    private BigDecimal payAmount;
}
