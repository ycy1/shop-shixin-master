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
 * 入库单明细（一行一个规格，SKU 粒度，形状同采购单明细）
 *
 * 明细跟着入库单走，不再有 user_id；归属、数据范围全判主表。
 * 改单是"整单明细重存"（软删旧的再插新的），所以一单的明细会被反复重写，历史版本留在 is_deleted=1 的行里。
 *
 * 这里没有指向 shop_erp_purchase_item 的那一列（原始 DDL 就没有），所以"入了哪几行采购明细"
 * 是按 sku_id 对上采购单的（见 ShopErpPurchaseInServiceImpl#validate）：同一商品的入库量
 * 按这张采购单下所有入库单累加，合计不超过它的采购数量。
 *
 * quantity 是"这张单登记要入多少"，received_quantity 是"这张单实际已经入进去多少"，
 * 差值就是还剩多少没入 —— 入库动作（ShopErpPurchaseInService#receive）只动后者，
 * 所以一张单可以先入 3 件、过两天再入剩下的 7 件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_purchase_in_item")
@ApiModel(value = "入库单明细")
public class ShopErpPurchaseInItem extends BaseEntity {

    @ApiModelProperty(value = "入库单ID（shop_erp_purchase_in.id）")
    private Long receiptId;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id，由采购单明细带出）")
    private Long skuId;

    @ApiModelProperty(value = "入库数量（这张单登记的计划入库数），必须大于 0，且同一商品不超过采购单里该商品的采购数量")
    private Integer quantity;

    // 服务端维护、表单不给填：入库动作按「本次入库数」累加进来，改单时由服务层按商品重新分配回各行
    // （0 ≤ 它 ≤ quantity）。有了它，一张单才能分几次入
    @ApiModelProperty(value = "这张单已实际入库数量（由「入库」动作累加，非表单字段）")
    private Integer receivedQuantity;

    @ApiModelProperty(value = "单价（成本价，从采购明细带过来，可改；不拿商品售价覆盖）")
    private BigDecimal price;

    @ApiModelProperty(value = "小计 = 数量 × 单价（服务端算，前端传了也不认）")
    private BigDecimal amount;

    // 只给「入库」那个接口用：请求体里这一行的本次入库数。不是本表列 —— 累计结果写回 received_quantity
    @ApiModelProperty(value = "本次入库数（只在确认入库时提交，服务端累加到 receivedQuantity）")
    @TableField(exist = false)
    private Integer thisQuantity;

    // 不是本表列：XML 经 shop_product_sku 那一跳联 shop_product 带出来，商品被删了就显示占位文案
    @ApiModelProperty(value = "商品名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String productName;

    @ApiModelProperty(value = "商品编码（联表回填，非本表列）")
    @TableField(exist = false)
    private String productCode;

    // 同样不是本表列，且要多联一跳 shop_product_sku：这一行只记 sku_id，规格文本在 SKU 那一行上
    // （一单里同一商品的不同规格是几行，不带这一格就分不清哪行是哪行）
    @ApiModelProperty(value = "规格文本（联 shop_product_sku 回填，非本表列）")
    @TableField(exist = false)
    private String specText;
}
