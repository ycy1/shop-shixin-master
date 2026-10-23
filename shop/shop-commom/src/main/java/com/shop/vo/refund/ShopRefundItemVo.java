package com.shop.vo.refund;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款单的退货商品行（sys_refund.extra 里那份 JSON 数组的一条）
 *
 * 建单那一刻按订单明细算好、写定，之后不改：
 * 它是"这次申请退的是哪几行、各退几件、各退多少钱"的唯一说法。
 * 为什么存 JSON 不建 sys_refund_item 明细表：这一轮只有退款回调成功时会按它把
 * shop_order_item.returned_quantity 累加上（累计结果落在那一列，是"这一行退掉几件"的终局说法），
 * 中间态没人按行查、也没人按行统计，为它单开一张表不划算。
 * 后台退款详情要看退的哪几行时，按 orderItemId 现联 shop_order_item 就行（行是软删的，永远读得到）。
 */
@Data
@ApiModel(value = "退款单退货商品行")
public class ShopRefundItemVo {

    @ApiModelProperty(value = "订单明细行ID(shop_order_item.id)")
    private Long orderItemId;

    @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)，退款回调建退货入库单时按这一条规格记账")
    private Long skuId;

    @ApiModelProperty(value = "这一行这次退的件数")
    private Integer quantity;

    @ApiModelProperty(value = "这一行这次退的金额（服务端按明细的成交金额−优惠，再按件数摊出来，两位小数）")
    private BigDecimal amount;
}
