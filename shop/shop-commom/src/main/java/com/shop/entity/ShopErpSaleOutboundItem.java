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
 * 销售出库单明细（一行对应订单明细里的一行商品，见 ShopOrderItem）
 *
 * 一张订单可以有几行同商品不同规格，所以本表和 shop_order_item 是**按行**一一对应
 * （order_item_id），不是按商品汇总 —— 规格文本因此不用在这张表上再存一份，联过去取就行
 * （只有上面那条例外取不到订单明细，退回联 shop_product_sku 现取，见下面 specText 那格的取舍）。
 *
 * 3 采购退货 那一路是个例外：退的是采购单进来的货，没有对应的订单明细行，所以 order_item_id 为空、
 * 这一行按 规格 + 仓库 记（建单时把同一规格在采购明细里的行合并成一行）。
 *
 * 归属、数据范围全判主表，所以明细没有 user_id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_sale_outbound_item")
@ApiModel(value = "销售出库单明细")
public class ShopErpSaleOutboundItem extends BaseEntity {

    @ApiModelProperty(value = "出库单ID（shop_erp_sale_outbound.id）")
    private Long outboundId;

    @ApiModelProperty(value = "来源订单明细ID（shop_order_item.id）：1 线上购买 那一路的行身份，表单不给改；"
            + "3 采购退货 等没有订单明细的来源为 NULL（那一行按 skuId + warehouseId 记）")
    private Long orderItemId;

    @ApiModelProperty(value = "出库仓库ID（shop_erp_warehouse.id，支付回调按这一行订单明细的门店定的那家仓）；扣的就是这一仓的库存")
    private Long warehouseId;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id）")
    private Long skuId;

    @ApiModelProperty(value = "计划出库数量（来源=1 时是订单明细的购买数量；来源=3 采购退货 时是这次要退的数量），必须大于 0")
    private Integer quantity;

    // 服务端维护、表单不给填：出库动作按「本次出库数」累加进来（0 ≤ 它 ≤ quantity）。
    // 有了它，一张单才能分几次出
    @ApiModelProperty(value = "这张单已实际出库数量（由「出库」动作累加，非表单字段）")
    private Integer outboundQuantity;

    // 来源=1 线上购买：这一列和 amount 都是支付回调按订单明细的实付金额写进去的（单价按件摊），
    // 出库动作只累加 outbound_quantity，不会拿该仓库存的加权进货成本去覆盖它
    // 来源=3 采购退货：建退货单时带的是这一行商品的采购单价（退给供应商按进价结），弹窗里可改
    @ApiModelProperty(value = "出库成本价（来源=1 线上购买时=订单明细实付金额÷购买数量，支付回调写入，前端传了也不认；"
            + "来源=3 采购退货时=采购单价，在退货弹窗里可改；其它来源由操作员在出库时填）")
    private BigDecimal costPrice;

    @ApiModelProperty(value = "小计（服务端算，前端传了也不认）：来源=1 时就是这一行订单明细的实付金额（建单时定死，出库不改）；"
            + "其它来源 = 计划出库数量 × 出库成本价")
    private BigDecimal amount;

    // 只给「出库」那个接口用：请求体里这一行的本次出库数。不是本表列 —— 累计结果写回 outbound_quantity
    @ApiModelProperty(value = "本次出库数（只在确认出库时提交，服务端累加到 outboundQuantity）")
    @TableField(exist = false)
    private Integer thisQuantity;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用
    @ApiModelProperty(value = "商品名称（联 shop_product 带出，非本表列）")
    @TableField(exist = false)
    private String productName;

    @ApiModelProperty(value = "商品编码（联 shop_product 带出，非本表列）")
    @TableField(exist = false)
    private String productCode;

    @ApiModelProperty(value = "仓库名称（联 shop_erp_warehouse 带出，非本表列）")
    @TableField(exist = false)
    private String warehouseName;

    // 一行一仓，出库动作按行扣这一仓的库存，所以仓库冻没冻（2 盘点中 / 3 调拨中）要逐行看得清
    // （判据见 com.shop.utils.WarehouseUtils）
    @ApiModelProperty(value = "仓库状态（联 shop_erp_warehouse.status 带出，字典 warehouse_status，非本表列；2 盘点中 / 3 调拨中 时这一行出不了货）")
    @TableField(exist = false)
    private Integer warehouseStatus;

    // 优先取订单明细那份下单快照：历史出库单不该跟着商品后来改规格文案一起变。
    // 只有 3 采购退货 那一路没有订单明细行（快照为空），才退回联 shop_product_sku 现取这一条规格的文本
    @ApiModelProperty(value = "规格文本（优先联 shop_order_item 的下单快照，取不到时联 shop_product_sku 现取，非本表列）")
    @TableField(exist = false)
    private String specText;

    @ApiModelProperty(value = "该仓当前可用库存（联 shop_erp_inventory 带出 = stock - locked_stock，非本表列，只当出库上限提示用）")
    @TableField(exist = false)
    private Integer availableStock;

    // 只有「采购退货」那条链用（草稿接口按商品汇总采购单的入库进度回填，提交时服务端按同一口径复核）：
    // 退货退的是"真到过的货"，所以上限是已入库数扣掉名下退货单已登记的数量，而不是库存数
    @ApiModelProperty(value = "来源采购单里这个商品已入库的数量（采购退货草稿回填，非本表列）")
    @TableField(exist = false)
    private Integer receivedQuantity;

    @ApiModelProperty(value = "这个商品还能退的数量 = 已入库 − 这张采购单名下退货单已登记的量（采购退货草稿回填，非本表列，明细行 quantity 的上限）")
    @TableField(exist = false)
    private Integer returnableQuantity;
}
