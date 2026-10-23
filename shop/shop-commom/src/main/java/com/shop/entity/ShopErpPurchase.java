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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购单（进销存管理 -> 采购单管理）
 *
 * 三个列不由表单决定：purchase_no 新增时服务端生成、total_amount / total_quantity 按明细累加
 * （见 ShopErpPurchaseServiceImpl#recalculate），所以前端这三处只读。
 * audit_time / receive_time 也不进表单：状态第一次改成 1/2 时服务端补，之后不覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_purchase")
@ApiModel(value = "采购单")
public class ShopErpPurchase extends BaseEntity {

    @ApiModelProperty(value = "采购单号，唯一；新增时不传则由系统生成（PURCHASE_+时间戳）")
    private String purchaseNo;

    @ApiModelProperty(value = "供应商ID（shop_erp_supplier.id），必填")
    private Long supplierId;

    // 所属门店：表单必填，服务层 validate 挡（列故意留 NULL 可空，库里那几张老单没这个信息，不加 NOT NULL）。
    // 建入库单时要按它加载可选仓库（这家门店名下的仓 + 没挂门店的共用仓），所以采购单一经建立就不再改门店
    @ApiModelProperty(value = "所属门店ID（shop_store.id），必填；入库单按它加载可选仓库")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long storeId;

    // 负责的 ERP 操作员：表单里那一格「ERP操作员」直接写这列，所以是 IGNORED 不是留痕列的 NEVER
    // （全局 not_empty 会把 null 整个从 SET 里跳过，清空就写不回去）。
    // ERP 账号（account_type=5）改不动：服务层钉成登录 id / 原值，同供应商的绑定账号
    @ApiModelProperty(value = "负责的ERP操作员用户ID（sys_user.id，account_type=5）；ERP账号按它认领自己那几条")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "采购总金额（服务端按明细累加，前端传了也不认）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "采购总数量（服务端按明细累加，前端传了也不认）")
    private Integer totalQuantity;

    @ApiModelProperty(value = "采购状态：0待审核 1已审核 4待入库 2已入库 3已退货（字典 purchase_status；4/2 由入库进度回写，不是手工选的）")
    private Integer status;

    // 可空且表单里能清掉 → IGNORED（同 remark）；下面两个时间列故意不加：
    // 全局 not_empty 让"null 不进 SET"，服务端只在第一次进入该状态时补值，之后就抹不掉已记的时间
    @ApiModelProperty(value = "预计到货日期")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    private LocalDate expectDate;

    @ApiModelProperty(value = "审核时间（状态置为1时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime auditTime;

    @ApiModelProperty(value = "入库时间（状态置为2时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime receiveTime;

    // 表单里选「ERP操作员」时会带出该账号的昵称和手机号（口径同供应商页的绑定账号），带出后仍可改：
    // 存的是录单时的快照，之后账号资料改了不回刷已录的单子
    @ApiModelProperty(value = "联系人（负责这单的ERP操作员，选操作员时带出，可改）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactName;

    @ApiModelProperty(value = "联系电话（选操作员时带出，可改）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactPhone;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用（同 ShopDelivery 那批冗余名称）
    @ApiModelProperty(value = "供应商名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String supplierName;

    @ApiModelProperty(value = "所属门店名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String storeName;

    @ApiModelProperty(value = "ERP操作员昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "采购明细（新增/修改整体提交，查询详情时带出，非本表列）")
    @TableField(exist = false)
    private List<ShopErpPurchaseItem> items;

    @ApiModelProperty(value = "查询：单号/备注/供应商名称关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：创建时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：创建时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String endTime;
}
