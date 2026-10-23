package com.shop.vo.browse;

import com.shop.entity.ShopConsumerBrowse;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 浏览足迹列表/详情视图对象
 *
 * 本表只存 (消费者, 商品, 浏览时间)，商品名称/主图/单价/分类名称都是查询时联表带出的。
 * 足迹记的是整件商品（不是某个规格），而单价在 1:N 的 shop_product_sku 上，所以这一格取的是
 * 这件商品名下最便宜那条规格的价，看起来像个区间起点；真要逐规格的价格得去商品详情那一条看。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "浏览足迹视图对象")
public class ShopBrowseVo extends ShopConsumerBrowse {

    @ApiModelProperty(value = "商品编码（联表带出）")
    private String productCode;

    @ApiModelProperty(value = "商品名称（联表带出，商品已删除时为空）")
    private String productName;

    @ApiModelProperty(value = "商品主图（联表带出）")
    private String mainImage;

    @ApiModelProperty(value = "商品单价（取这件商品名下最低那条 shop_product_sku.product_price）")
    private BigDecimal productPrice;

    @ApiModelProperty(value = "商品分类名称（按商品的 category_id 联表带出）")
    private String categoryName;

    @ApiModelProperty(value = "消费者昵称（联表带出）")
    private String nickname;

    @ApiModelProperty(value = "消费者用户名（联表带出）")
    private String username;
}
