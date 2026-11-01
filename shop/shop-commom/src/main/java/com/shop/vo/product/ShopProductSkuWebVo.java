package com.shop.vo.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品SKU Web 端视图（详情页规格选择器那一条）
 *
 * 跟 ShopProductWebVo 同一口径：不带登录态也能读，所以只留「给消费者看」的格 ——
 * 成本价、安全/最大库存、预警开关、留痕字段一律不出现。
 * 这一条的 id 就是加购和下单要传的那个 skuId。
 */
@Data
@ApiModel(value = "商品SKU Web 端视图")
public class ShopProductSkuWebVo {

    @ApiModelProperty(value = "SKU id（加购/下单传这一格）")
    private Long id;

    @ApiModelProperty(value = "规格文本（详情页那排按钮上显示的字，默认规格是空）")
    private String specText;

    @ApiModelProperty(value = "该规格的主图URL(=skuImages 组首那张)；NULL=沿用商品主图")
    private String skuImage;

    @ApiModelProperty(value = "这一档规格的轮播图，MySQL json 列原样给回的 JSON 文本：[{\"title\":\"\",\"url\":\"\"}]")
    private String skuImages;

    @ApiModelProperty(value = "这一档规格的详情图（详情页「商品详情」那一块整组铺开），同样是 json 列原样给回的 JSON 文本")
    private String detailImages;

    @ApiModelProperty(value = "单价")
    private BigDecimal productPrice;

    @ApiModelProperty(value = "可用库存（这一条SKU在所有仓库的 库存-锁定 之和，查询时现汇总）")
    private Integer availableStock;

    @ApiModelProperty(value = "这一条规格的销量（shop_product_sku.sales_count，支付回调按规格累加）")
    private Integer salesCount;

    @ApiModelProperty(value = "买这个规格可获得的积分，0=不发积分")
    private Integer gainPoints;

    @ApiModelProperty(value = "是否可销售：1可销售 0不可销售（这一条不看它，所以 0 的也会出现，由页面自己决定怎么表现）")
    private Integer saleEnable;
}
