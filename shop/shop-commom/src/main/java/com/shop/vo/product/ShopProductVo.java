package com.shop.vo.product;

import com.shop.entity.ShopProduct;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品列表/详情视图对象（SPU 粒度）
 *
 * 重构后商品只带「这件商品」的那几格（上下架 status、轮播 carousel_enable 现在是 shop_product 的列，
 * 由父类带出），单价、规格文本、库存预警参数都在 shop_product_sku 上，商品列表/详情这一层看不到
 * （唯一例外是文件末尾那三格：销售单的规格候选一行就是一个规格，得把 SKU 那一侧的值给出去）。
 * 价格区间是查询时按名下 SKU 现汇总出来的（取 MIN/MAX），不是 shop_product 的列，所以只出现在 VO 里。
 * 销量、可用库存和成本价刻意不汇总到这一层：销量记在每个 SKU 上，后两个更是「一个 SKU × 一个仓」那一层的账，
 * 摊成 SPU 一个数会骗人（多规格的商品里，缺货的那一档恰恰是人想买的），要看就看 SKU 那份（ShopProductSkuVo）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "商品视图对象")
public class ShopProductVo extends ShopProduct {

    @ApiModelProperty(value = "最低价（名下所有SKU的单价取最小，查询时现算；一条SKU都没有为 null）")
    private BigDecimal priceMin;

    @ApiModelProperty(value = "最高价（名下所有SKU的单价取最大，查询时现算；一条SKU都没有为 null）")
    private BigDecimal priceMax;

    @ApiModelProperty(value = "所属类目名称（联表查询回填）")
    private String categoryName;

    @ApiModelProperty(value = "品牌名称（联表查询回填）")
    private String brandName;

    @ApiModelProperty(value = "品牌LOGO（联表查询回填）")
    private String brandLogo;

    @ApiModelProperty(value = "所属门店名称（联表查询回填）")
    private String storeName;

    // 以下三格只有「销售单明细的商品候选」那一条 SQL 会填（ShopErpSaleMapper#selectProductOptions）：
    // 候选已经改成一行一个规格，选下来落进销售明细的是 skuId，销售单价的默认值是这一条规格的
    // product_price，规格文本给下拉那一行标出来是哪个规格。
    // 商品列表/详情那几条查询仍是 SPU 粒度（一行一件商品，规格在它名下），这三格都是 null，前端别读
    @ApiModelProperty(value = "规格ID（只有销售单明细的商品候选会填，那一行是一个规格）")
    private Long skuId;

    @ApiModelProperty(value = "规格文本（同上，联 shop_product_sku 带出）")
    private String specText;

    @ApiModelProperty(value = "该规格的单价（同上，取自 shop_product_sku.product_price，当销售单价默认值用；"
            + "商品列表那侧看的是汇总出来的 priceMin/priceMax，不是这一格）")
    private BigDecimal productPrice;
}
