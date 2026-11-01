package com.shop.vo.product;

import com.shop.entity.ShopProductSku;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品SKU视图对象
 *
 * 本体的列都是 shop_product_sku 自己的；所属商品的编码/名称/主图和分类名、品牌名、门店名由联表带出，
 * 可用库存和成本价由 shop_erp_inventory 按**这一条 SKU** 在所有仓的账现汇总（前者求和、后者按库存加权平均）。
 * SKU 表单要回显商品那几格（规格挂在哪个商品下、没传 skuImage 时显示商品主图），所以这些字段不能省。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "商品SKU视图对象")
public class ShopProductSkuVo extends ShopProductSku {

    @ApiModelProperty(value = "所属商品编码（联表查询回填）")
    private String productCode;

    @ApiModelProperty(value = "所属商品名称（联表查询回填）")
    private String productName;

    @ApiModelProperty(value = "所属商品主图（联表查询回填；skuImage 为空时前端就拿它显示）")
    private String mainImage;

    @ApiModelProperty(value = "所属商品的上下架状态（联表 shop_product 带出）：1上架 0下架")
    private Integer productStatus;

    @ApiModelProperty(value = "所属类目名称（联表查询回填）")
    private String categoryName;

    @ApiModelProperty(value = "品牌名称（联表查询回填）")
    private String brandName;

    @ApiModelProperty(value = "所属门店名称（联表查询回填）")
    private String storeName;

    @ApiModelProperty(value = "可用库存（这一条SKU在所有仓库的 库存-锁定 之和，查询时现汇总）")
    private Integer availableStock;

    // 同样是联表现汇总的：成本价存在 shop_erp_inventory 的每个仓一行（入库动作各自按加权平均刷），
    // 这里按库存量加权汇总成一个数；一件都没入过时为 null（前端显示 '-'，不给兜成 0）
    @ApiModelProperty(value = "成本价（所有仓库按库存量加权平均，查询时现汇总；未入过货为 null）")
    private BigDecimal costPrice;
}
