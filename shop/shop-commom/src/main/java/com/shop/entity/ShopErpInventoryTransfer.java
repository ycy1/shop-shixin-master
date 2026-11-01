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
 * 库存调拨单（进销存管理 -> 库存管理 -> 库存调拨）
 *
 * 主子表里"服务端说了算"的那几组同入库单：transfer_no 新增时生成、total_quantity / total_amount
 * 按明细累加（见 ShopErpInventoryTransferServiceImpl#recalculate）、三个时间列由状态推进时补。
 *
 * 和入库单最不一样的一件事是这张单会**改仓库自己的状态**：新建就把调出、调入两家仓都置成
 * 3 调拨中（WarehouseUtils 里那一档会把出入库、下单分仓全拦掉），单子走到 3 已完成 / 4 已取消
 * 才把它们复位成 1 启用 —— 而且只有没有其它还在跑（0/1/2）的调拨单引用这家仓时才复位，
 * 否则先完成的那张会把别人那张的冻结解掉。建单要求两家仓当时都是 1 启用，就是为了这条复位链走得通。
 *
 * 库存的两下都由「调拨」动作直接落（不经过入库单/出库单），见
 * ShopErpInventoryService#transferOut / #transferIn 和库存流水的 6 调拨出库 / 5 调拨入库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_inventory_transfer")
@ApiModel(value = "库存调拨单")
public class ShopErpInventoryTransfer extends BaseEntity {

    /** 状态：待审核（字典 transfer_status）。刚建好还没批，只有这一档能改能删 */
    public static final int STATUS_PENDING = 0;

    /** 状态：已审核。批下来了，操作列那颗「调拨」从这一档开始能动账 */
    public static final int STATUS_AUDITED = 1;

    /** 状态：调出中。已经调出过至少一次，但还有规格没调满 */
    public static final int STATUS_OUTING = 2;

    /** 状态：已完成。所有明细都调满了，两家仓跟着走复位 */
    public static final int STATUS_DONE = 3;

    /** 状态：已取消。待审核/已审核时作废，一笔库存都没动 */
    public static final int STATUS_CANCELLED = 4;

    /**
     * 这单还在不在"占用着两家仓的调拨中状态"：0/1/2 三档算在跑（3 已完成、4 已取消 腾出来了）。
     * 复位仓库前按这个口径数一遍别单，见 ShopErpInventoryTransferServiceImpl#releaseWarehouses
     */
    public static boolean inFlight(Integer status) {
        if (status == null) {
            return false;
        }
        int value = status;
        return value == STATUS_PENDING || value == STATUS_AUDITED || value == STATUS_OUTING;
    }

    /** 调拨类型：普通调拨（字典 transfer_type，本列默认值） */
    public static final int TYPE_NORMAL = 1;

    /** 调拨类型：紧急调拨（缺货补仓） */
    public static final int TYPE_URGENT = 2;

    /** 调拨类型：门店间调拨（两家门店仓之间挪货） */
    public static final int TYPE_BETWEEN_STORE = 3;

    @ApiModelProperty(value = "调拨单号，唯一；新增时不传则由系统生成（TRANSFER_+时间戳）")
    private String transferNo;

    @ApiModelProperty(value = "调出仓库ID（shop_erp_warehouse.id），必填；明细候选按这个仓的可用库存收窄")
    private Long fromWarehouseId;

    @ApiModelProperty(value = "调入仓库ID（shop_erp_warehouse.id），必填；不能和调出仓库是同一家")
    private Long toWarehouseId;

    // 负责的 ERP 操作员：表单那一格直接写这列（IGNORED），语义同入库单的"入库操作人"，不是留痕列。
    // ERP 账号（account_type=5）改不动：服务层钉成登录 id / 原值，列表也只按这一列收窄给他看
    @ApiModelProperty(value = "负责的ERP操作员用户ID（sys_user.id，account_type=5）；ERP 账号按它认领自己操作的调拨单")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "调拨总数量（服务端按明细累加，前端传了也不认）")
    private Integer totalQuantity;

    @ApiModelProperty(value = "调拨总成本（服务端按明细累加：各行 数量 × 调出仓成本价，前端传了也不认）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "调拨类型：1普通调拨 2紧急调拨 3门店间调拨（字典 transfer_type；不传按 1 算）")
    private Integer transferType;

    @ApiModelProperty(value = "状态：0待审核 1已审核 2调出中 3已完成 4已取消（字典 transfer_status；"
            + "0 由表单起、1 由「审核」推、2/3 由「调拨」按明细进度推、4 由「取消」置，都不给手工选）")
    private Integer status;

    @ApiModelProperty(value = "调拨原因")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String reason;

    // 三个时间列都故意不加 IGNORED：全局 not_empty 让 null 不进 UPDATE 的 SET，
    // 所以"状态第一次进到那一档时补一次、之后不覆盖"（口径同采购单的 audit_time / 入库单的 receive_time）
    @ApiModelProperty(value = "审核时间（状态第一次置为1时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime auditTime;

    @ApiModelProperty(value = "调出时间（状态第一次置为2时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime outTime;

    @ApiModelProperty(value = "入库时间（状态第一次置为3时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime receiveTime;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用（同入库单那批冗余名称）
    @ApiModelProperty(value = "调出仓库名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String fromWarehouseName;

    @ApiModelProperty(value = "调入仓库名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String toWarehouseName;

    // 建单之后仓库可能被人在仓库管理里改成盘点中/禁用，列表要把仓现在的状态说清楚，
    // 「调拨」按钮点不点得动也按它判（wf.status 是仓自己的状态，t.status 是单据流转状态，两码事）
    @ApiModelProperty(value = "调出仓库状态（联 shop_erp_warehouse.status 带出，字典 warehouse_status，非本表列）")
    @TableField(exist = false)
    private Integer fromWarehouseStatus;

    @ApiModelProperty(value = "调入仓库状态（同上，非本表列）")
    @TableField(exist = false)
    private Integer toWarehouseStatus;

    @ApiModelProperty(value = "操作员昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    // 列表要显示"已调出 / 计划"这一格、还要判断「调拨」「取消」按钮能不能点，但列表接口不返回明细，
    // 所以由 XML 里一条关联子查询把明细的已调出数量汇总出来（口径同入库单的 totalReceived）
    @ApiModelProperty(value = "已调出总数（子查询汇总明细的 out_quantity，非本表列）")
    @TableField(exist = false)
    private Integer totalOut;

    @ApiModelProperty(value = "已调入总数（子查询汇总明细的 receive_quantity，非本表列；和 totalOut 同增同停，"
            + "两个都摊出来这一页才说得出「调拨是把两侧一次记完」）")
    @TableField(exist = false)
    private Integer totalReceive;

    @ApiModelProperty(value = "调拨明细（新增/修改整体提交，查询详情时带出，非本表列）")
    @TableField(exist = false)
    private List<ShopErpInventoryTransferItem> items;

    @ApiModelProperty(value = "查询：调拨单号/原因/备注/调出仓名/调入仓名/操作员名关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：仓库ID（命中它作为调出仓或调入仓的那几张单，只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private Long warehouseId;

    @ApiModelProperty(value = "查询：创建时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：创建时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String endTime;
}
