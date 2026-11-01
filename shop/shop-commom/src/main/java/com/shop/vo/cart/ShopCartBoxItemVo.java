package com.shop.vo.cart;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 一整盒里的一个款式 —— 按盒 id 现查 shop_trendy_box_item 那份款式清单带出来的，购物车行上不存快照
 *
 * 读的两处同一份口径：前台购物车页展开看的那一叠、统一下单把一整盒拆成每款一条明细的那一份
 *（都走 ShopTrendyBoxStyleService）。名字和图片取的是那台盒当前的配置 —— 运营改了款式，
 * 车上那一行跟着变，这是「一整盒 = 这台盒现在装的东西」这个口径的代价，也是它不用回填旧行的原因。
 */
@Data
@ApiModel(value = "购物车整盒行的一个款式")
public class ShopCartBoxItemVo {

    @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)；款式没挂规格就不进这一份清单（下单锁不了库存）")
    private Long skuId;

    @ApiModelProperty(value = "款式名称（那台盒上当前配的那份）")
    private String styleName;

    @ApiModelProperty(value = "款式图片（那台盒上当前配的那份）")
    private String styleImage;

    @ApiModelProperty(value = "这一款在一盒里几件（当前口径固定 1 件，页面把它乘以盒数显示）")
    private Integer quantity;
}
