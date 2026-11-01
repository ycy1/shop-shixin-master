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
 * 调拨明细（一行一个规格，形状同入库单明细）
 *
 * 明细跟着调拨单走，不再有 user_id；归属、数据范围全判主表。
 * 改单是"整单明细重存"（软删旧的再插新的），所以一单的明细会被反复重写，历史版本留在 is_deleted=1 的行里。
 *
 * sku_id 是这一行的身份（调多少件就得说是哪一条规格），product_id 是它所属商品那一跳的冗余
 * —— 原始 DDL 两列都留着，所以这里也两列都写：商品名/编码本来就是经 sk 再联 pr 取，
 * 冗余那一格用来按商品统计（idx_transfer_product）。
 *
 * quantity 是"这张单登记要调多少"，out_quantity 是"已经从调出仓挪走多少"，receive_quantity 是
 * "已经记到调入仓多少"。「调拨」动作（ShopErpInventoryTransferService#transfer）把这三个数一次记完
 * —— 调出仓减的那一下和调入仓加的那一下是同一个事务里的同一批数量，所以后两列永远相等，
 * 差值（quantity − out_quantity）就是这一行还剩多少没调。
 *
 * cost_price 从**调出仓**那一行库存带出（建单时带、改单时重新带），调进调入仓时按它做加权平均；
 * 这一行在调出仓还没有库存行时带不出价，服务层直接拦（没价就没成本口径）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_inventory_transfer_item")
@ApiModel(value = "调拨明细")
public class ShopErpInventoryTransferItem extends BaseEntity {

    @ApiModelProperty(value = "调拨单ID（shop_erp_inventory_transfer.id）")
    private Long transferId;

    @ApiModelProperty(value = "商品ID（shop_product.id，SPU 粒度；服务端按 sku_id 回填，前端传了也不认）")
    private Long productId;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id，必填：库存行是按规格记的）")
    private Long skuId;

    @ApiModelProperty(value = "调拨数量（这张单登记的计划数），必须大于 0")
    private Integer quantity;

    // 服务端维护、表单不给填：「调拨」动作按「本次调拨数」累加进来（0 ≤ 它 ≤ quantity），
    // 有了它一张单才能分几次调
    @ApiModelProperty(value = "已从调出仓调出的数量（由「调拨」动作累加，非表单字段）")
    private Integer outQuantity;

    @ApiModelProperty(value = "已记到调入仓的数量（和 outQuantity 同增同停，由「调拨」动作累加，非表单字段）")
    private Integer receiveQuantity;

    @ApiModelProperty(value = "调拨成本价（建单/改单时从调出仓那行库存带出；调拨时按它给调入仓加权）")
    private BigDecimal costPrice;

    @ApiModelProperty(value = "小计 = 调拨数量 × 成本价（服务端算，前端传了也不认）")
    private BigDecimal amount;

    // 只给「调拨」那个接口用：请求体里这一行的本次调拨数。不是本表列 —— 累计结果写回 out/receive_quantity
    @ApiModelProperty(value = "本次调拨数（只在确认调拨时提交，服务端两侧一次累加）")
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

    // 也不是本表列：详情那条 SQL 按调拨单的调出仓再联一跳 shop_erp_inventory 带出来，
    // 「调拨」弹窗里这一格说清"这个仓现在还能挪几件"（可用库存 = 总库存 − 锁定库存）
    @ApiModelProperty(value = "调出仓这条规格的可用库存（按主表的调出仓库联 shop_erp_inventory 回填，非本表列；"
            + "查的时候没给仓库则为 null）")
    @TableField(exist = false)
    private Integer availableStock;
}
