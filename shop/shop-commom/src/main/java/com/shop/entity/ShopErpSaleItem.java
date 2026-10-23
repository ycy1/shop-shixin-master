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
 * 对外销售单明细（一行一个规格，SKU 粒度）
 *
 * 明细跟着销售单走，所以不再有 user_id；归属、数据范围全判主表。
 * 改单是"整单明细重存"（软删旧的再插新的），所以一单的明细会被反复重写，历史版本留在 is_deleted=1 的行里。
 * 也正因为重存会换行 id，shipped_quantity 要在重存前按商品搬一次（见 ShopErpSaleServiceImpl#carryOverShipped）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_sale_item")
@ApiModel(value = "对外销售单明细")
public class ShopErpSaleItem extends BaseEntity {

    @ApiModelProperty(value = "销售单ID（shop_erp_sale.id）")
    private Long saleId;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id）")
    private Long skuId;

    @ApiModelProperty(value = "销售数量，必须大于 0")
    private Integer quantity;

    @ApiModelProperty(value = "已出库数量（0 ≤ 它 ≤ quantity；由出库单的出库动作累加，新增/改单的请求体里带什么都不认）")
    private Integer shippedQuantity;

    @ApiModelProperty(value = "销售单价（不是商品售价，所以服务端不拿商品价覆盖）")
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

    // 同样不是本表列，且要多联一跳 shop_product_sku：这一行只记 sku_id，规格文本在 SKU 那一行上
    // （一张销售单里同一商品的不同规格是几行，不带这一格就分不清哪行是哪行）
    @ApiModelProperty(value = "规格文本（联 shop_product_sku 回填，非本表列）")
    @TableField(exist = false)
    private String specText;

    // 不是本表列，也只有「商品出库」选销售单那个接口会填
    // （见 ShopErpSaleOutboundService#selectSaleItems）：这行还剩多少能出
    @ApiModelProperty(value = "可出库数量（销售数量扣掉这张销售单下其它出库单已登记的量，非本表列）")
    @TableField(exist = false)
    private Integer availableQuantity;
}
