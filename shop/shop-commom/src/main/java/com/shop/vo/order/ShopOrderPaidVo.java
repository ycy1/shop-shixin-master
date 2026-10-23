package com.shop.vo.order;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 支付回调的结果：告诉调用方这张单现在是已支付、以及给它建出来的那张出库单是哪一张
 */
@Data
@ApiModel(value = "支付回调结果")
public class ShopOrderPaidVo {

    @ApiModelProperty(value = "订单ID(sys_order.id)")
    private Long orderId;

    @ApiModelProperty(value = "订单号")
    private String orderNo;

    @ApiModelProperty(value = "回调处理后的订单状态：1已支付（重复回调时订单可能已经走得更远，原样返回不往回拽）")
    private Integer status;

    @ApiModelProperty(value = "出库单ID（shop_erp_sale_outbound.id）")
    private Long outboundId;

    @ApiModelProperty(value = "出库单号")
    private String outboundNo;

    @ApiModelProperty(value = "本次是不是刚建的出库单：false 表示这张订单早就有出库单了（重复回调），原单返回")
    private Boolean outboundCreated;
}
