package com.shop.dto.refund;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款结果通知参数（退款到账/失败，服务端据此把退款单翻成终态，成功时回写订单明细并建退货入库单）
 *
 * 只收"哪张退款单、结果如何、渠道那边的单号是多少"：
 * 退款成功时间由服务端取当前时间；退的是哪几行、各退几件读的是退款单建单时落在 sys_refund.extra 里的那份快照，
 * 退货入库单的仓库/商品/数量/单价一律由服务端按订单明细和库存算，参数里传了也不认。
 *
 * 注意：这个端点和支付回调一样在 /api/** 白名单里（不走 Sa-Token），所以调用方必须自己保证通知是真的
 * —— 真接微信那一路时要在验签通过后再调本接口，绝不能把 URL 直接暴露给前端或网关。
 * 本期还没有接渠道，所以调用方是后台（人工确认钱已退）或本地脚本，口径同上。
 */
@Data
@ApiModel(value = "退款回调参数")
public class ShopRefundCallbackDto {

    @ApiModelProperty(value = "退款单ID(sys_refund.id)，和 refundNo 至少给一个；两个都给以 id 为准")
    private Long refundId;

    @ApiModelProperty(value = "退款单号(sys_refund.refund_no)，没给 id 时按它找单")
    private String refundNo;

    @ApiModelProperty(value = "退款是否成功：true 成功（翻 4）、false 失败（翻 5，只记原因不回写明细），不传按 true")
    private Boolean success;

    @ApiModelProperty(value = "第三方退款单号（微信/支付宝返回的那一串），成功时原样落到 sys_refund.third_refund_no")
    private String thirdRefundNo;

    @ApiModelProperty(value = "实际退款金额（元）。传了就和库里 sys_refund.actual_refund_amount 比对，"
            + "对不上直接拒；不传跳过校验（人工确认那类调用拿不到渠道金额时用）")
    private BigDecimal refundAmount;

    @ApiModelProperty(value = "失败原因（success=false 时写进 sys_refund.fail_reason，说清是哪一挡退回来的）")
    private String failReason;
}
