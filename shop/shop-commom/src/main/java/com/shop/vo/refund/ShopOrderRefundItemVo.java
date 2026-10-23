package com.shop.vo.refund;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款单上的一行退货商品（后台「我的订单 → 退款单」弹窗里展开那张表的一条）
 *
 * 件数和金额来自 sys_refund.extra 那份 JSON（建单那一刻按订单明细写定的申请内容，见 {@link ShopRefundItemVo}），
 * 商品名称/编码/规格/主图**不在**那份 JSON 里 —— 那些是 shop_order_item 上的下单快照，读的时候现联带出来，
 * 免得同一份信息存两处、商品改名时两边对不上。
 *
 * 单独开这一个 VO 而不是给 {@link ShopRefundItemVo} 加几格：那个类是 extra JSON 的读写结构，
 * 塞进展示字段就等于把 null 也一起序列化进库里那列。
 */
@Data
@ApiModel(value = "退款单的退货商品行（申请内容 + 商品快照）")
public class ShopOrderRefundItemVo {

    @ApiModelProperty(value = "订单明细行ID(shop_order_item.id)")
    private Long orderItemId;

    @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)，extra 里存的那一格，明细行已被删除时只能报这个")
    private Long skuId;

    @ApiModelProperty(value = "商品名称（现联 shop_order_item.product_name，下单时的快照）")
    private String productName;

    @ApiModelProperty(value = "商品编码（现联 shop_product.product_code，商品已删时为空）")
    private String productCode;

    @ApiModelProperty(value = "规格（现联 shop_order_item.spec_text）")
    private String specText;

    @ApiModelProperty(value = "商品主图（现联 shop_order_item.image）")
    private String image;

    @ApiModelProperty(value = "这一行这次退的件数")
    private Integer quantity;

    @ApiModelProperty(value = "这一行这次退的金额（建单时按明细的成交金额−优惠、再按件数摊出来的，两位小数）")
    private BigDecimal amount;
}
