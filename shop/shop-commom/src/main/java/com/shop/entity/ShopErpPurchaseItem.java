package com.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购单明细（一行一个规格，SKU 粒度）
 *
 * 明细跟着采购单走，所以不再有 user_id；归属、数据范围全判主表。
 * 改单是"整单明细重存"（软删旧的再插新的），所以一单的明细会被反复重写，历史版本留在 is_deleted=1 的行里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_purchase_item")
@ApiModel(value = "采购单明细")
public class ShopErpPurchaseItem extends BaseEntity {

    @ApiModelProperty(value = "采购单ID（shop_erp_purchase.id）")
    private Long purchaseId;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id）")
    private Long skuId;

    @ApiModelProperty(value = "采购数量，必须大于 0")
    private Integer quantity;

    @ApiModelProperty(value = "已入库数量（0 ≤ 它 ≤ quantity；由入库动作累加，新增/改单的请求体里带什么都不认）")
    private Integer receivedQuantity;

    @ApiModelProperty(value = "采购单价（不是商品售价，所以服务端不拿商品价覆盖）")
    private BigDecimal price;

    @ApiModelProperty(value = "小计 = 数量 × 单价（服务端算，前端传了也不认）")
    private BigDecimal amount;

    // 不是本表列：XML 经 shop_product_sku 那一跳联 shop_product 带出来，商品被删了就显示占位文案
    @ApiModelProperty(value = "商品名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String productName;

    @ApiModelProperty(value = "商品编码（联表回填，非本表列）")
    @TableField(exist = false)
    private String productCode;

    // 同样不是本表列，且必须多联一跳 shop_product_sku 才拿得到：明细行只记 sku_id，
    // 规格文本在 SKU 那一行上。一行一个规格，光有商品名称分不出同一名下的哪一行，所以非带不可
    @ApiModelProperty(value = "规格文本（联 shop_product_sku 回填，非本表列）")
    @TableField(exist = false)
    private String specText;

    // 不是本表列，也只有「商品入库」选采购单那个接口会填
    // （见 ShopErpPurchaseInService#selectPurchaseItems）：这行还剩多少能入
    @ApiModelProperty(value = "可入库数量（采购数量扣掉这张采购单下其它入库单已登记的量，非本表列）")
    @TableField(exist = false)
    private Integer availableQuantity;
}
