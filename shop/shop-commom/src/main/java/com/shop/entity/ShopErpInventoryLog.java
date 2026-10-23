package com.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存流水（商品库存 -> 操作列「库存日志」）：一次库存变动一行，只写不改不删
 *
 * 这张表是 shop_erp_inventory 那本账的流水页：账上只有当前数（stock 一列），
 * 谁在什么时候因为哪张单把它从多少改成多少，全在这一张表里。所以：
 * 1. 只有查询接口。没有新增/修改/删除 —— 一行流水必须由一次真实的库存变动带出来，
 *    手工插一条"库存多了 10 件"的记录，账和流水就对不上了。
 * 2. 写入方有四组动作：ShopErpInventoryServiceImpl#inbound（商品入库的「入库」动作）和
 *    #outbound（商品出库的「出库」动作），以及同一份里给库存调拨用的 #transferIn / #transferOut、
 *    给库存盘点用的 #checkIn / #checkOut（调拨和盘点都直接挪库存，不经过入库单/出库单，
 *    所以它们都不走前两个方法那道仓库状态闸 —— 盘点中/调拨中的仓本来就被自己的单据冻着）。
 *    前两条都按单据的来源分档：入库单 inbound_source=1 线上退货
 *    记 4 退货入库、其余记 1 采购入库；出库单那一路按 outbound_source 分：1 线上购买 记 3 下单出库、
 *    2 销售出库 记 2 销售出库、3 采购退货 记 9 采购退货出库（见 ShopErpSaleOutboundServiceImpl#outbound）。
 *    调拨那两路固定记 6 调拨出库（调出仓，带负号）+ 5 调拨入库（调入仓），一次「调拨」必然两行，
 *    biz_id / biz_no 是那张调拨单（见 ShopErpInventoryTransferServiceImpl#transfer）。
 *    盘点那两路固定记 7 盘盈（实盘多于账面，正数）/ 8 盘亏（少于账面，带负号），
 *    biz_id / biz_no 是那张盘点单，一次「调整」里每一条有差异的行一行（见
 *    ShopErpInventoryCheckServiceImpl#adjust）—— 账实相符的行不动账，所以也不记流水。
 *    注意 3 这一档记的是**出库那一下**（货真离仓、总库存减少）：统一下单只涨 locked_stock、
 *    总库存没动，那一刻不记流水，所以名字叫"下单出库"≠发生在下单时。
 * 3. before_stock / after_stock 记的是 shop_erp_inventory.stock（总库存），不是可用库存 ——
 *    入库时 before + change = after，出库时 change_quantity 是负数、同样自洽；
 *    两个值取自那次 CAS 更新用的同一份快照，不额外查一次库。
 * 4. user_id 只记"这次是谁动的"，故意不做数据范围（和库存表一样：流水是共享的一本账，
 *    按人收窄会让别人入的货在这行账上凭空消失）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_inventory_log")
@ApiModel(value = "库存流水")
public class ShopErpInventoryLog extends BaseEntity {

    /** 变动类型：采购入库。由商品入库的「入库」动作写（入库单 inbound_source=2 采购入库 那一路） */
    public static final int CHANGE_TYPE_PURCHASE_IN = 1;

    /**
     * 变动类型：退货入库。同样由商品入库的「入库」动作写，只是那张入库单的 inbound_source=1（线上退货，
     * 货是买家退回来的、不是买回来的），流水那一页要能看出这批货为什么进的仓，
     * 见 ShopErpPurchaseInServiceImpl#receive
     */
    public static final int CHANGE_TYPE_RETURN_IN = 4;

    /** 变动类型：销售出库。由商品出库的「出库」动作写（change_quantity 为负） */
    public static final int CHANGE_TYPE_SALE_OUT = 2;

    /**
     * 变动类型：下单出库。同样由「出库」动作写，只是那张出库单的 outbound_source=1（买家在线上下的单，
     * 单子由支付回调建），和 2 销售出库 的区别是"卖出去的是线上订单"而不是后台按销售单开的那一张；
     * 名字里的"下单"指这批货的来路，流水仍然是出库那一刻记的，见 ShopErpSaleOutboundServiceImpl#outbound
     */
    public static final int CHANGE_TYPE_ONLINE_OUT = 3;

    /**
     * 变动类型：采购退货出库。同样由「出库」动作写，只是那张出库单的 outbound_source=3（把货退回给供应商），
     * 流水那一页要能看出这批货不是因为卖出去才离仓的 —— 所以出库动作按来源分这一档，
     * 见 ShopErpSaleOutboundServiceImpl#outbound
     */
    public static final int CHANGE_TYPE_PURCHASE_RETURN_OUT = 9;

    /**
     * 变动类型：调拨入库。由库存调拨的「调拨」动作写（调入仓那一侧），和 1 采购入库 的区别是
     * 这批货不是买回来的、是从自己家另一个仓挪过来的，所以来源单据记的是调拨单，
     * 见 ShopErpInventoryTransferServiceImpl#transfer
     */
    public static final int CHANGE_TYPE_TRANSFER_IN = 5;

    /**
     * 变动类型：调拨出库。同一条调拨动作的另一侧（调出仓），change_quantity 为负；
     * 一次调拨必然 6 + 5 两行、数量相同，只看一行会以为货凭空少了
     */
    public static final int CHANGE_TYPE_TRANSFER_OUT = 6;

    /**
     * 变动类型：盘盈。由库存盘点的「调整」动作写（实盘多于账面那几行，正数），
     * 和 1 采购入库 的区别是这批货不是买回来的、是清点时发现的账外货，
     * 来源单据记的是盘点单，见 ShopErpInventoryCheckServiceImpl#adjust
     */
    public static final int CHANGE_TYPE_CHECK_PROFIT = 7;

    /**
     * 变动类型：盘亏。同一次「调整」的另一头（实盘少于账面那几行），change_quantity 为负；
     * 和 2 销售出库 的区别是货不是卖出去的、是对不上账了，所以流水这一页得单列一档
     */
    public static final int CHANGE_TYPE_CHECK_LOSS = 8;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id）；和 warehouseId 一起定位被记的那行库存")
    private Long skuId;

    @ApiModelProperty(value = "仓库ID（shop_erp_warehouse.id）")
    private Long warehouseId;

    @ApiModelProperty(value = "这次变动的操作人（sys_user.id）；只记人，不做数据范围")
    private Long userId;

    @ApiModelProperty(value = "变动类型（字典 inventory_change_type）：1采购入库 2销售出库 3下单出库 4退货入库 5调拨入库 6调拨出库 7盘盈 8盘亏 9采购退货出库")
    private Integer changeType;

    @ApiModelProperty(value = "变动数量（带符号：入库为正、出库为负）")
    private Integer changeQuantity;

    @ApiModelProperty(value = "变动前总库存（服务端算，前端传了也不认）")
    private Integer beforeStock;

    @ApiModelProperty(value = "变动后总库存 = 变动前 + 变动数量（服务端算，前端传了也不认）")
    private Integer afterStock;

    @ApiModelProperty(value = "关联业务ID（入库那两档是入库单 shop_erp_purchase_in.id，出库那三档是出库单 "
            + "shop_erp_sale_outbound.id，调拨那两档是调拨单 shop_erp_inventory_transfer.id）")
    private Long bizId;

    @ApiModelProperty(value = "关联业务单号（入库那两档是入库单号 receipt_no，出库那三档是出库单号 outbound_no，"
            + "调拨那两档是调拨单号 transfer_no）")
    private String bizNo;

    @ApiModelProperty(value = "备注；这一版没有写入方")
    private String remark;

    // 以下都不是本表列：XML 联表带出来给列表回显用（同 ShopErpInventory 的那几列）
    @ApiModelProperty(value = "商品名称（经 shop_product_sku 的 product_id 联 shop_product 回填，非本表列）")
    @TableField(exist = false)
    private String productName;

    @ApiModelProperty(value = "商品编码（经 shop_product_sku 的 product_id 联 shop_product 回填，非本表列）")
    @TableField(exist = false)
    private String productCode;

    @ApiModelProperty(value = "规格文本（联 shop_product_sku 回填，非本表列）；流水记的就是某个规格的进出，"
            + "这一格得跟着行走，否则同商品几个规格的流水在列表上长得一模一样")
    @TableField(exist = false)
    private String specText;

    // 只当查询条件用：本表没有商品列（一行是「一条规格 × 一个仓」的一次变动），按商品筛就是筛这条规格的所属商品，
    // 命中该商品名下所有规格的流水（口径同 ShopErpInventory 上那个同名字段）
    @ApiModelProperty(value = "查询：按所属商品筛（含它名下所有规格），非本表列")
    @TableField(exist = false)
    private Long productId;

    @ApiModelProperty(value = "仓库名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String warehouseName;

    @ApiModelProperty(value = "操作人昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;
}
