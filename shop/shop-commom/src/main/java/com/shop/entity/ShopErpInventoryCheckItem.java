package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 盘点明细（一行一个规格，形状同调拨明细）
 *
 * 明细跟着盘点单走，不再有 user_id；归属、数据范围全判主表。
 *
 * 三个数是一条链：book_quantity 是**建单那一刻**从 shop_erp_inventory 那行库存快照下来的账面数，
 * actual_quantity 是人在「修改」弹窗里逐行录的实盘数（没录就是 NULL，代表还没盘到），
 * diff_quantity = 实盘 − 账面（服务端算，正为盘盈、负为盘亏；实盘没录时也是 0，别当差异看，
 * 按 status 认这一行到底盘过没有）。改单不会刷新 book_quantity —— 账面必须停在盘点开始那一刻才对得上账，
 * 中途出入库动的库存由下一张盘点单去发现。
 *
 * cost_price 同样在建单时从那一行库存带出，diff_amount = 差异数量 × 成本价（负差异就是负金额，
 * 主表那两个金额列才分得开盈/亏）。这一行在这家仓压根没有库存行时连账面数都带不出，建单那一步就被拦；
 * 库存行的 cost_price 允许是 NULL（没入过账），那种行能建、能录实盘（diff_amount 一起留 NULL），
 * 但到「调整」真要动账时必须有一个价，取不到就拦下来（会先按库存行当下的值补一次）。
 *
 * status 三档：0 待盘点（刚登记进盘点范围）→ 1 已盘点（录了实盘）→ 2 已调整（在「调整」里确认过）。
 * 账实相符的行不动账，但也要点一次确认才进 2 —— 无差异同样是"有人认过这一笔"的一个事实。
 *
 * sku_id 在原始 DDL 里可空（注释写的"无规格为NULL"），但这一页的明细候选来自 shop_erp_inventory，
 * 那一行必然挂着某一条规格，所以服务层仍要求必填。另外唯一键 uk_check_sku 里有 NULL 不参与判重
 * （多条 NULL sku 在 MySQL 里能并存），所以"同一张单里一条规格只能有一行"这件事只能由服务层保证。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_inventory_check_item")
@ApiModel(value = "盘点明细")
public class ShopErpInventoryCheckItem extends BaseEntity {

    /** 明细状态：待盘点（字典 check_item_status，登记进盘点范围、还没录实盘） */
    public static final int STATUS_PENDING = 0;

    /** 明细状态：已盘点（录了实盘、算出了差异，还没调账） */
    public static final int STATUS_COUNTED = 1;

    /** 明细状态：已调整（在「调整」里确认过：有差异的已经动了库存，无差异的只是认过这一笔） */
    public static final int STATUS_ADJUSTED = 2;

    @ApiModelProperty(value = "盘点单ID（shop_erp_inventory_check.id）")
    private Long checkId;

    @ApiModelProperty(value = "商品ID（shop_product.id，SPU 粒度；服务端按 sku_id 回填，前端传了也不认）")
    private Long productId;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id，必填：库存行是按规格记的）")
    private Long skuId;

    @ApiModelProperty(value = "账面库存（建单时从这家仓那行库存快照，之后不刷新；服务端填，前端传了也不认）")
    private Integer bookQuantity;

    // 可空且表单里能清掉（= 这一行还没盘）→ 必须 IGNORED，否则全局 not_empty 会把 null 从 SET 里跳过
    @ApiModelProperty(value = "实盘库存（人工录入，NULL 表示还没盘到这一行）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer actualQuantity;

    @ApiModelProperty(value = "差异数量 = 实盘 - 账面（服务端算，正为盘盈、负为盘亏，前端传了也不认）")
    private Integer diffQuantity;

    @ApiModelProperty(value = "成本价（建单时从这家仓那行库存带出，用来算差异金额，也是流水那两行的口径）")
    private BigDecimal costPrice;

    @ApiModelProperty(value = "差异金额 = 差异数量 × 成本价（服务端算，负差异就是负金额，前端传了也不认）")
    private BigDecimal diffAmount;

    // 同样给 IGNORED：只在有差异的行上填，改单时想抹掉重填也得写得回 NULL
    @ApiModelProperty(value = "差异原因：1正常损耗 2记录错误 3丢失 4其他（字典 diff_reason；"
            + "有差异的行在「调整」里必须选一个，无差异的行可空）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String diffReason;

    @ApiModelProperty(value = "状态：0待盘点 1已盘点 2已调整（字典 check_item_status；"
            + "0 由建单起、1 由录实盘推、2 由「调整」置，都不给手工选）")
    private Integer status;

    // 只给「调整」那个接口用：请求体里这一行的"这次调不调"（true 才动账）。不是本表列 ——
    // 一行整行一次调平，所以没有"本次调整数"那一格，累计结果就是 status 翻成 2
    @ApiModelProperty(value = "本次是否调整（只在确认调整时提交，服务端按它挑出要动账的行）")
    @TableField(exist = false)
    private Boolean thisAdjust;

    // 不是本表列：XML 经 shop_product_sku 那一跳联 shop_product 带出来，商品被删了就显示占位文案
    @ApiModelProperty(value = "商品名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String productName;

    @ApiModelProperty(value = "商品编码（联表回填，非本表列）")
    @TableField(exist = false)
    private String productCode;

    // 同样不是本表列，且要多联一跳 shop_product_sku：这一行只记 sku_id，规格文本在 SKU 那一行上
    @ApiModelProperty(value = "规格文本（联 shop_product_sku 回填，非本表列）")
    @TableField(exist = false)
    private String specText;

    // 也不是本表列：详情那条 SQL 按盘点单的仓库再联一跳 shop_erp_inventory 带出来。
    // 正常情况它应该等于 book_quantity（建单就把这家仓冻成 2 盘点中，出入库和下单分仓都进不来），
    // 不相等就说明盘点期间账被动过（或者这张单建得太早），「调整」弹窗要把它亮出来让人先看清：
    // 调账加/减的是这一行的 diff_quantity（盘点认下来的那个差异），不是拿实盘数去覆盖现在的库存
    @ApiModelProperty(value = "这家仓这条规格现在的总库存（按主表的仓库联 shop_erp_inventory 回填，非本表列；"
            + "查的时候没给仓库则为 null）")
    @TableField(exist = false)
    private Integer currentStock;

    @ApiModelProperty(value = "这家仓这条规格现在的可用库存 = 总库存 − 锁定库存（同上，非本表列）。"
            + "盘亏要减的是总库存，但锁定那一份是下单占着、还没出库的货，减狠了那些单就发不出，"
            + "所以这一格用来提前看出「盘亏数超过能动的那部分」")
    @TableField(exist = false)
    private Integer availableStock;
}
