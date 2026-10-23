package com.shop.vo.refund;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款回调处理结果
 *
 * 一次调用做完的事都在这儿报回去：退款单翻到哪一档、明细回写了几行几件、
 * 订单有没有整单退成「已退款」、整单退完时返了几张券、这次退了几分数、退回钱包多少钱、以及建出来的那几张退货入库单（按发货仓一家一张）。
 * 重复通知不会重建单子，created=false 那一条就是库里早就有的那一张（幂等凭据见服务实现类注释）。
 */
@Data
@ApiModel(value = "退款回调处理结果")
public class ShopRefundCallbackVo {

    @ApiModelProperty(value = "退款单ID / 单号")
    private Long refundId;

    private String refundNo;

    @ApiModelProperty(value = "退款单处理后的状态：4退款成功 5退款失败（字典 refund_status）")
    private Integer status;

    @ApiModelProperty(value = "实际退款金额（库里那一份，回调传了金额只做比对、不改这一格）")
    private BigDecimal actualRefundAmount;

    @ApiModelProperty(value = "第三方退款单号（回调带进来原样落库的那个）")
    private String thirdRefundNo;

    @ApiModelProperty(value = "退款成功时间（服务端取当前时间；失败时为 NULL）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime refundTime;

    @ApiModelProperty(value = "失败原因（success=false 时写进 sys_refund.fail_reason 的那一句）")
    private String failReason;

    @ApiModelProperty(value = "这次回写了哪几行订单明细（行数）/ 共几件 —— 失败或重复通知时为 0")
    private Integer returnedItemCount;

    private Integer returnedQuantity;

    @ApiModelProperty(value = "订单状态：整单退满才推成 4 已退款，否则还是原值（NULL=这次没碰主单）")
    private Integer orderStatus;

    @ApiModelProperty(value = "随退款退回用户的优惠券张数：只有这一单整单退成「已退款」才返券，部分退款和重复通知都是 0")
    private Integer giveBackCouponCount;

    @ApiModelProperty(value = "随退款退回用户的积分：只算退还的抵扣那一份，按这一批退掉的件数占该行购买件数的比例折算（不等整单退满；没用过分或重复通知是 0）。"
            + "当初为这批货送出去的分是反向收回的另一步，不进这个数，看积分流水")
    private Integer giveBackPoints;

    @ApiModelProperty(value = "退回钱包可用余额的钱数：只有这一单是余额支付（pay_type=4）才有，退的是这一张退款单的实退金额，"
            + "一张单分几批退就走几次（其余支付方式、失败和重复通知都是 0）")
    private BigDecimal giveBackBalance;

    @ApiModelProperty(value = "按发货仓建出来的退货入库单（一仓一张；明细里落的是这一单退给这家仓的货）")
    private List<Receipt> receipts;

    /** 一张退货入库单的抬头（shop_erp_purchase_in，inbound_source=1 线上退货，状态 0 待入库） */
    @Data
    @ApiModel(value = "退货入库单一行")
    public static class Receipt {

        @ApiModelProperty(value = "入库单ID / 单号")
        private Long receiptId;

        private String receiptNo;

        @ApiModelProperty(value = "收货仓库ID / 名称（这一单退回来的货当初是从这家仓出去的）")
        private Long warehouseId;

        private String warehouseName;

        @ApiModelProperty(value = "这张单登记要入库的件数")
        private Integer totalQuantity;

        @ApiModelProperty(value = "这张单是这次建的还是重复通知拿回来的原单：false=库里早就有了")
        private Boolean created;
    }
}
