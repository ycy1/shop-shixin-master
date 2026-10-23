package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.core.BaseEntity;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售出库单（进销存管理 -> 库存管理 -> 商品出库）
 *
 * 形状与入库单是一对镜像（入库是货进仓、出库是货出仓），所以照 ShopErpPurchaseIn 那一套写：
 * 主表记单据头，明细记逐行；单号、总数、总金额、状态一律服务端算，表单传了也不认；
 * "可多次出库"靠明细的 outbound_quantity 累加，主表状态由明细进度算（0待出库 → 1部分出库 → 2已出库）。
 *
 * 两处和入库单不一样，改之前先看这里：
 * 1. **仓库在明细上、不在主表**。这一单的货可能从几个仓分别发（每行的仓是支付回调建这张单时按商品所属
 *    门店定的：门店仓优先、其次按这一单的收货地址（sys_order.address_id）就近，地址用不上才按门店经纬度就近，
 *    见 ShopOrderPaidServiceImpl#routeWarehouse
 *    和 ShopOrderItem#warehouseId），所以 warehouse_id 是明细列，主表只有 order_id / order_no 指回来源单据。
 * 2. **数据范围故意不按 user_id 收窄**（和采购单/仓库/入库单那三件套相反）。线上订单这一路的单子
 *    由支付回调自动生成，那一刻没有后台登录人，user_id 恒为 NULL；再按它收窄的话，ERP 账号在列表里
 *    一张线上出库单都看不到，等于死单。所以 user_id 在这里只记"谁点的出库"。
 *
 * 来源单据是 order_id / order_no 这一对，按 outbound_source 决定指向哪张表：
 * 1 线上购买 → sys_order（支付回调建，见 shop-api 的 ShopOrderPaidService），
 * 2 销售出库 → shop_erp_sale（「商品出库」页那颗「新建」按对外销售单建，见 ShopErpSaleOutboundService#insert），
 * 3 采购退货 → shop_erp_purchase（「采购单」页那颗「采购退货」建，见 ShopErpPurchaseService#createPurchaseReturn，
 * 退的是当初从这家供应商进来的货，所以明细行没有 order_item_id、按 商品+仓库 记）。
 * 三路互斥、不会同时有值。另外 4 调拨出库 / 5 报损出库 / 6 其他 也是同一颗「新建」建的（见 insert），
 * 那三路没有来源单据可指、商品逐行手挑，所以 order_id / order_no 就是 NULL。
 * ⚠ 共用一列的代价：id 各自一套编号，拿到 order_id 不看出库来源就去联
 * sys_order 会串到一张不相干的订单上（采购单 id=7 和订单 id=7 是两张不同的单据），
 * 所以联表的地方一律带上对应的 `AND so.outbound_source = 1/2/3`。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_sale_outbound")
@ApiModel(value = "销售出库单")
public class ShopErpSaleOutbound extends BaseEntity {

    /** 出库来源：线上购买（支付回调按订单明细自动建单固定用这一个）；这一路的单子删不掉，见服务层 deleteByIds */
    public static final int SOURCE_ONLINE_ORDER = 1;

    /** 出库来源：销售出库（「商品出库」页那颗「新建」按对外销售单建，order_id/order_no 里存的是销售单） */
    public static final int SOURCE_SALE_OUTBOUND = 2;

    /** 出库来源：采购退货（「采购单」页那颗「采购退货」建，order_id/order_no 里存的是采购单，见 ShopErpPurchaseService#createPurchaseReturn） */
    public static final int SOURCE_PURCHASE_RETURN = 3;

    /** 出库来源：调拨出库（「商品出库」页那颗「新建」手工加行建的三路之一，没有来源单据，order_id/order_no 为 NULL） */
    public static final int SOURCE_TRANSFER_OUT = 4;

    /** 出库来源：报损出库（同 4，手工加行那一路） */
    public static final int SOURCE_LOSS_OUT = 5;

    /** 出库来源：其他（同 4，手工加行那一路，也是这一颗能建的最后一档） */
    public static final int SOURCE_OTHER = 6;

    @ApiModelProperty(value = "出库单号，唯一；新增时不传则由系统生成（SALE_OUTBOUND_+时间戳+3位随机）")
    private String outboundNo;

    // 刻意不加 IGNORED：来源单据建单那一刻就定死了，表单里根本不传这两列，永远不该被"清空"
    @ApiModelProperty(value = "来源单据ID，按 outbound_source 决定指向：1 线上购买 → sys_order.id；"
            + "2 销售出库 → shop_erp_sale.id；3 采购退货 → shop_erp_purchase.id（三路互斥，联表必须带来源条件）；"
            + "4 调拨出库 / 5 报损出库 / 6 其他 是后台手工建的，没有来源单据，这一列为 NULL")
    private Long orderId;

    @ApiModelProperty(value = "来源单据单号（建单时冗余：1 线上购买 = 订单号，2 销售出库 = 销售单号，3 采购退货 = 采购单号；"
            + "4/5/6 手工建的三路为 NULL）")
    private String orderNo;

    @ApiModelProperty(value = "出库来源：1线上购买 2销售出库 3采购退货 4调拨出库 5报损出库 6其他（字典 sale_outbound_source）")
    private Integer outboundSource;

    @ApiModelProperty(value = "计划出库总数（服务端按明细累加，前端传了也不认）")
    private Integer totalQuantity;

    @ApiModelProperty(value = "出库总金额（服务端按明细 amount 累加，前端传了也不认）；"
            + "来源=1 线上购买时在支付回调建单那一刻就等于订单的实付金额（sys_order.pay_amount）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "状态：0待出库 1部分出库 2已出库（字典 sale_outbound_status；服务端按明细的出库进度算，表单里不给选）")
    private Integer status;

    // 刻意不加 IGNORED：全局 not_empty 让 null 不进 UPDATE 的 SET，
    // 状态从 2 退回 0/1 时已记过的出库时间抹不掉（口径同入库单的 receive_time）
    @ApiModelProperty(value = "出库时间（状态第一次置 2 时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime outboundTime;

    // 只是留痕（这张表不按它收窄），所以给 IGNORED：后台手工建的单要能把操作人清空
    @ApiModelProperty(value = "出库操作人用户ID（sys_user.id，account_type=5）；支付回调自动建单时为 NULL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 以下都不是本表列：XML 联表/子查询带出来给列表和详情回显用
    @ApiModelProperty(value = "下单人（联 sys_order → sys_user 带出，非本表列；只有来源=1 有值）")
    @TableField(exist = false)
    private String buyerName;

    @ApiModelProperty(value = "所属订单状态（联 sys_order.status 带出，字典 order_status，非本表列；只有来源=1 有值）")
    @TableField(exist = false)
    private Integer orderStatus;

    // 供应商名不冗余成列（和 order_no 相反：那一列存的是来源单据的单号，改名前建的历史单会留着旧号）：
    // 列表/详情是 LEFT JOIN shop_erp_purchase → shop_erp_supplier 现取的，只有来源=3 的那几行有值
    @ApiModelProperty(value = "来源采购单的供应商名（联 shop_erp_purchase → shop_erp_supplier 带出，非本表列；只有来源=3 采购退货 有值）")
    @TableField(exist = false)
    private String supplierName;

    // 来源销售单的门店名（销售单上只存 store_id，名称联表现取，同上面那个供应商名）
    @ApiModelProperty(value = "来源销售单的销售门店名（联 shop_erp_sale → shop_store 带出，非本表列；只有来源=2 销售出库 有值）")
    @TableField(exist = false)
    private String storeName;

    @ApiModelProperty(value = "已出库总数（子查询汇总明细的 outbound_quantity，非本表列）")
    @TableField(exist = false)
    private Integer totalOut;

    // 只有列表那条 SQL 带这一格（判据见 com.shop.utils.WarehouseUtils）：>0 就把「出库」置灰，
    // 详情和出库弹窗里逐行看是哪一行的仓冻着（明细那侧带的是 warehouseStatus）
    @ApiModelProperty(value = "出库仓正在盘点/调拨（仓库状态 2 或 3）的明细行数（子查询带出，非本表列，只给列表判断「出库」按钮能不能点）")
    @TableField(exist = false)
    private Integer frozenRows;

    // 「生成配送单」按钮的判据之一就是它（配送单在统一下单时就有了，但要等支付回调换绑才挂到这张出库单上：
    // 所以这一格有值 = 钱已收；一出库单一配送，配送表里最多一条）。
    // 只在查询时带出，表单不提交；updateById 走的是实体，全局 not_empty 也不会把它写进 SET
    @ApiModelProperty(value = "这张单绑定的配送单ID（子查询联 shop_delivery 带出，非本表列；NULL=这一单还没支付、还没换绑）")
    @TableField(exist = false)
    private Long deliveryId;

    @ApiModelProperty(value = "这张单绑定的配送单号（子查询带出，非本表列，按钮收掉/置灰时要说清是哪一条）")
    @TableField(exist = false)
    private String deliveryNo;

    @ApiModelProperty(value = "这张单绑定的配送单状态（子查询带出，字典 delivery_status，非本表列；0 待派单 = 还没人点「确认」）")
    @TableField(exist = false)
    private Integer deliveryStatus;

    @ApiModelProperty(value = "出库明细（查询详情时带出，非本表列）")
    @TableField(exist = false)
    private List<ShopErpSaleOutboundItem> items;

    @ApiModelProperty(value = "查询：出库单号/来源单据号（订单号或采购单号）/备注关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：创建时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：创建时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String endTime;
}
