package com.shop.vo.order;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 统一下单的结果：前端拿到订单号和金额就够了，支付那一步由支付接口按 orderNo 再查
 */
@Data
@ApiModel(value = "统一下单结果")
public class ShopOrderCreateVo {

    @ApiModelProperty(value = "订单ID(sys_order.id)")
    private Long orderId;

    @ApiModelProperty(value = "订单号")
    private String orderNo;

    @ApiModelProperty(value = "订单状态：0待支付 1已支付 2已完成 3已取消 4已退款 5已关闭（字典 order_status），下单后固定 0")
    private Integer status;

    @ApiModelProperty(value = "订单总金额（= 各行小计之和 + 配送费）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "优惠金额合计（只记优惠券）")
    private BigDecimal discountAmount;

    /** 现在 totalAmount = 货值 + 这一格，payAmount = totalAmount − discountAmount − pointsAmount（下单时按规则算好，见 ShopOrderDeliveryServiceImpl） */
    @ApiModelProperty(value = "配送费（已含在总金额里）")
    private BigDecimal deliveryFee;

    @ApiModelProperty(value = "这一单用掉的积分（没用就是 0；下单这一刻已冻进账户的 frozen_points，支付成功才真扣走）")
    private Integer usedPoints;

    @ApiModelProperty(value = "这些积分抵掉多少钱（按 sys_config 的 points_exchange 折，不进 discount_amount）")
    private BigDecimal pointsAmount;

    @ApiModelProperty(value = "实付金额 = 总金额 − 优惠 − 积分抵扣")
    private BigDecimal payAmount;

    @ApiModelProperty(value = "明细行数")
    private Integer itemCount;
}
