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
 * 库存盘点单（进销存管理 -> 库存管理 -> 库存盘点）
 *
 * 主子表里"服务端说了算"的那几组同调拨单：check_no 新增时生成，六个数
 * （total_sku_count / diff_sku_count / profit_quantity / loss_quantity / profit_amount / loss_amount）
 * 全由明细算出来回写（见 ShopErpInventoryCheckServiceImpl#recalculate），前端传了也不认。
 * 差异那四个数是绝对值口径：profit_* 只加正差异、loss_* 只加负差异，所以两个数都是正的，
 * diff_sku_count 数的是"账实不符的那几行"，跟金额无关。
 *
 * 这张单和调拨单一样会**改仓库自己的状态**：新建就把盘点仓置成 2 盘点中（WarehouseUtils 那一档把
 * 出入库、下单分仓全拦掉），单子走到 2 已完成 / 3 已取消 才复位成 1 启用 —— 而且只有没有其它还在跑
 * （0/1）的盘点单引用这家仓时才复位，并且只认它现在确实是 2（被调拨单冻成 3 的那家不该被这张单顺手解开）。
 * 建单要求这家仓当时是 1 启用，就是为了这条复位链走得通。
 *
 * 但也有一处和调拨单相反：调拨动的是两家仓之间的货，盘点动的是**这一家仓自己的账**，
 * 所以盘整张单期间仓是冻着的（除了自家「调整」，谁都别想动它的库存行）。
 * 调整那一下直接改 shop_erp_inventory、不经过入库单/出库单，见
 * ShopErpInventoryService#checkOut / #checkIn 和库存流水的 7 盘盈 / 8 盘亏。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_inventory_check")
@ApiModel(value = "库存盘点单")
public class ShopErpInventoryCheck extends BaseEntity {

    /** 状态：待审核（字典 check_status）。刚建好还没批，只有这一档能改范围、能删 */
    public static final int STATUS_PENDING = 0;

    /** 状态：盘点中。批下来了，「修改」弹窗录实盘和操作列那颗「调整」都从这一档开始 */
    public static final int STATUS_STOCKTAKING = 1;

    /** 状态：已完成。每一行明细都在「调整」里确认过了，仓库跟着走复位 */
    public static final int STATUS_DONE = 2;

    /** 状态：已取消。还没调过账时作废，一笔库存都没动 */
    public static final int STATUS_CANCELLED = 3;

    /**
     * 这单还在不在"占用着那家仓的盘点中状态"：0/1 两档算在跑（2 已完成、3 已取消 腾出来了）。
     * 复位仓库前按这个口径数一遍别单，见 ShopErpInventoryCheckServiceImpl#releaseWarehouse
     */
    public static boolean inFlight(Integer status) {
        if (status == null) {
            return false;
        }
        int value = status;
        return value == STATUS_PENDING || value == STATUS_STOCKTAKING;
    }

    /** 盘点类型：全盘（字典 check_type，本列默认值，这家仓的全部规格都盘一遍） */
    public static final int TYPE_FULL = 1;

    /** 盘点类型：抽盘（挑一部分规格盘，日常抽查） */
    public static final int TYPE_SAMPLE = 2;

    /** 盘点类型：循环盘点（按周期滚动盘一部分规格） */
    public static final int TYPE_CYCLE = 3;

    @ApiModelProperty(value = "盘点单号，唯一；新增时不传则由系统生成（CHECK_+时间戳）")
    private String checkNo;

    @ApiModelProperty(value = "盘点仓库ID（shop_erp_warehouse.id），必填；明细候选就是这家仓的库存行")
    private Long warehouseId;

    // 负责的 ERP 操作员：表单那一格直接写这列（IGNORED，不选就是没记），语义同调拨单的"操作员"，
    // 不是留痕列。ERP 账号（account_type=5）改不动：服务层钉成登录 id / 原值，列表也只按这一列收窄给他看
    @ApiModelProperty(value = "负责的ERP操作员用户ID（sys_user.id，account_type=5）；ERP 账号按它认领自己盘的这几张单")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "盘点类型：1全盘 2抽盘 3循环盘点（字典 check_type；不传按 1 算）")
    private Integer checkType;

    @ApiModelProperty(value = "盘点SKU总数 = 明细行数（服务端按明细算，前端传了也不认）")
    private Integer totalSkuCount;

    @ApiModelProperty(value = "差异SKU数 = 实盘和账面不相等的明细行数（服务端算，非表单字段）")
    private Integer diffSkuCount;

    @ApiModelProperty(value = "盘盈总数量（服务端算：只加差异为正的那些行，所以恒 ≥ 0）")
    private Integer profitQuantity;

    @ApiModelProperty(value = "盘亏总数量（服务端算：只加差异为负的那些行、取绝对值，所以恒 ≥ 0）")
    private Integer lossQuantity;

    @ApiModelProperty(value = "盘盈总金额（服务端算：正差异 × 各行成本价，所以恒 ≥ 0）")
    private BigDecimal profitAmount;

    @ApiModelProperty(value = "盘亏总金额（服务端算：负差异那几行的差异金额取绝对值，所以恒 ≥ 0）")
    private BigDecimal lossAmount;

    @ApiModelProperty(value = "状态：0待审核 1盘点中 2已完成 3已取消（字典 check_status；"
            + "0 由表单起、1 由「审核」推、2 由「调整」按明细进度推、3 由「取消」置，都不给手工选）")
    private Integer status;

    // 两个时间列都故意不加 IGNORED：全局 not_empty 让 null 不进 UPDATE 的 SET，
    // 所以"状态第一次进到那一档时补一次、之后不覆盖"（口径同调拨单的 audit_time）
    @ApiModelProperty(value = "开始时间（状态第一次置为1盘点中时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间（状态第一次置为2已完成时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用（同调拨单那批冗余名称）
    @ApiModelProperty(value = "盘点仓库名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String warehouseName;

    // 建单之后仓可能被人在仓库管理里改成禁用/调拨中，列表要把仓现在的状态说清楚，
    // 「调整」按钮点不点得动也按它判（w.status 是仓自己的状态，c.status 是单据流转状态，两码事）
    @ApiModelProperty(value = "盘点仓库状态（联 shop_erp_warehouse.status 带出，字典 warehouse_status，非本表列）")
    @TableField(exist = false)
    private Integer warehouseStatus;

    @ApiModelProperty(value = "操作员昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    // 列表要显示"已调整 / 共几行"这一格、还要判断「调整」「取消」按钮能不能点，但列表接口不返回明细，
    // 所以由 XML 里一条关联子查询把明细里 status=2 的行数汇总出来（口径同调拨单的 totalOut）
    @ApiModelProperty(value = "已调整的行数（子查询汇总明细里 status=2 的行，非本表列；"
            + "等于 total_sku_count 时这张单就自动进 2 已完成，> 0 时就不许取消了）")
    @TableField(exist = false)
    private Integer adjustedCount;

    @ApiModelProperty(value = "盘点明细（新增/修改整体提交，查询详情时带出，非本表列）")
    @TableField(exist = false)
    private List<ShopErpInventoryCheckItem> items;

    // 一个输入框搜单号、仓库名、操作员名和备注，同调拨单那一格（明细里几千个规格不该被拿来当单号筛）
    @ApiModelProperty(value = "查询：盘点单号/仓库名称/操作员昵称/备注关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    // 这一页的时间筛选是**创建时间**，字段名不能叫 beginTime/endTime：本表就有 start_time / end_time
    // 两个真列（盘点起止），同名会把它们当查询条件覆盖掉（门店培训那页踩过同一坑，也是这么改名的）
    @ApiModelProperty(value = "查询：创建时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String createBeginTime;

    @ApiModelProperty(value = "查询：创建时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String createEndTime;
}
