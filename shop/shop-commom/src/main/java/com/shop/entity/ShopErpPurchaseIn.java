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
 * 入库单（进销存管理 -> 库存管理 -> 商品入库）
 *
 * 主子表里"服务端说了算"的三件事同采购单：receipt_no 新增时生成、total_amount / total_quantity
 * 按明细累加（见 ShopErpPurchaseInServiceImpl#recalculate）、receive_time 状态第一次进 2 时补。
 *
 * 还有一件：supplier_id 不是表单列，它由**来源=2 采购入库**时所选的采购单带出来（服务层按 purchase_id 回填），
 * 所以那一路的供应商永远和它来源的采购单一致，前端这一格只读回显。
 *
 * inbound_source 决定这张单是从哪儿来的货，也就是"挂哪张来源单据"：只有 2 采购入库 必须挂采购单
 * （供应商和明细候选都从它带），1 线上退货 由退款回调自动建，挂的是退款单（refund_id），
 * 没有采购单也没有供应商。
 * 镜像那一侧见 ShopErpSaleOutbound#outboundSource（那张表用 order_id 记来源单据）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_purchase_in")
@ApiModel(value = "入库单")
public class ShopErpPurchaseIn extends BaseEntity {

    /**
     * 入库来源（字典 inbound_source）。号按"先占号"排，和出库单那套是镜像的：
     * 1 线上退货 ↔ 出库单的 1 线上购买、2 采购入库 ↔ 3 采购退货。
     * 目前只有 1（退款回调建单）和 2（后台手工建单，也是本列默认值）有写入方。
     */
    public static final int SOURCE_ONLINE_RETURN = 1;
    public static final int SOURCE_PURCHASE = 2;
    public static final int SOURCE_SALE_RETURN = 3;
    public static final int SOURCE_TRANSFER_IN = 4;
    public static final int SOURCE_STOCKTAKE_PROFIT = 5;
    public static final int SOURCE_OTHER = 6;

    @ApiModelProperty(value = "入库单号，唯一；新增时不传则由系统生成（PURCHASE_IN_+时间戳）")
    private String receiptNo;

    @ApiModelProperty(value = "入库来源：1线上退货 2采购入库 3销货退回 4调拨入库 5盘盈入库 6其他"
            + "（字典 inbound_source；后台手工建单不传即 2，1 由退款回调建）")
    private Integer inboundSource;

    // 表单里选的那张采购单：可空列 + IGNORED（换单、清空都要能写回库）。
    // 现在真的有"没有采购单的入库"了：来源=1 线上退货 那张单是回调建的，只有仓库和明细
    @ApiModelProperty(value = "关联采购单ID（shop_erp_purchase.id）：入库来源=2 采购入库 时必填（供应商从它带），其它来源为空；明细由这张单带出")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long purchaseId;

    // 口径同出库单那侧的 order_id（支付回调建的出库单靠它挡住重复建单）：只存 id、不抄单号，
    // 退款单号联 sys_refund 现取。这一列同时是退款回调的幂等凭据（一张退款单一仓一张入库单）
    @ApiModelProperty(value = "关联退款单ID（sys_refund.id）：入库来源=1 线上退货 时由退款回调写入（重复回调按它拿回原单，不重建），其它来源为 NULL")
    private Long refundId;

    @ApiModelProperty(value = "供应商ID（服务端按采购单回填，前端传了也不认；没有采购单时为 NULL）")
    private Long supplierId;

    @ApiModelProperty(value = "入库仓库ID（shop_erp_warehouse.id），必填")
    private Long warehouseId;

    @ApiModelProperty(value = "入库总数（服务端按明细累加，前端传了也不认）")
    private Integer totalQuantity;

    @ApiModelProperty(value = "入库总金额（服务端按明细累加，前端传了也不认）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "状态：0待入库 1部分入库 2已入库（字典 purchase_in_status；服务端按明细的已入库进度算，表单里不给选）")
    private Integer status;

    // 故意不加 IGNORED：全局 not_empty 让 null 不进 UPDATE 的 SET，
    // 状态从 2 退回 0/1 时已记过的入库时间抹不掉（口径同采购单的 audit_time / receive_time）
    @ApiModelProperty(value = "入库时间（状态置为2时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime receiveTime;

    // 入库操作人：表单那一格直接写这列（IGNORED），语义同仓库的"负责的操作员"，不是留痕列。
    // ERP 账号（account_type=5）改不动：服务层钉成登录 id / 原值，列表也只按这一列收窄给他看
    @ApiModelProperty(value = "入库操作人用户ID（sys_user.id，account_type=5）；ERP 账号按它认领自己操作的入库单")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用（同采购单那批冗余名称）
    @ApiModelProperty(value = "关联采购单号（联表回填，非本表列）")
    @TableField(exist = false)
    private String purchaseNo;

    @ApiModelProperty(value = "关联退款单号（联表回填，非本表列；来源=1 线上退货 那几张单有值）")
    @TableField(exist = false)
    private String refundNo;

    @ApiModelProperty(value = "供应商名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String supplierName;

    @ApiModelProperty(value = "入库仓库名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String warehouseName;

    // 仓库那四档里只有 1 启用 能动账（判据见 com.shop.utils.WarehouseUtils），列表要把冻住的仓说清楚、
    // 「入库」按钮要点不动，所以联表时顺手把仓库自己的 status 带出来（pi.status 是单据流转状态，两码事）
    @ApiModelProperty(value = "入库仓库状态（联 shop_erp_warehouse.status 带出，字典 warehouse_status，非本表列；2 盘点中 / 3 调拨中 时这张单入不了库）")
    @TableField(exist = false)
    private Integer warehouseStatus;

    @ApiModelProperty(value = "操作人昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    // 列表要显示"已入库 / 计划"这一格、还要判断「入库」按钮能不能点，但列表接口不返回明细，
    // 所以由 XML 里一条关联子查询把明细的已入库数量汇总出来（同 storeName 那类只读列）
    @ApiModelProperty(value = "已入库总数（子查询汇总明细的 received_quantity，非本表列）")
    @TableField(exist = false)
    private Integer totalReceived;

    @ApiModelProperty(value = "入库明细（新增/修改整体提交，查询详情时带出，非本表列）")
    @TableField(exist = false)
    private List<ShopErpPurchaseInItem> items;

    @ApiModelProperty(value = "查询：入库单号/备注/采购单号/供应商名/仓库名关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：创建时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：创建时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String endTime;
}
