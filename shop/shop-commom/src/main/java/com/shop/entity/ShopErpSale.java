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
 * 对外销售单（进销存管理 -> 销售单管理）
 *
 * 和采购单是一套镜像，方向反过来：采购是「订进来的货要入库」，销售是「卖出去的货要出库」。
 * 三个列不由表单决定：sale_no 新增时服务端生成、total_amount / total_quantity 按明细累加
 * （见 ShopErpSaleServiceImpl#recalculate），所以前端这三处只读。
 * audit_time / ship_time 也不进表单：状态第一次改成 1/3 时服务端补，之后不覆盖。
 *
 * status 的 2 待出库 / 3 已出库 不是手工选的，是商品出库那边按登记量和实出量回写的
 * （见 ShopErpSaleOutboundService#refreshSaleStatus），表单里只能填 0/1/4
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_sale")
@ApiModel(value = "对外销售单")
public class ShopErpSale extends BaseEntity {

    @ApiModelProperty(value = "销售单号，唯一；新增时不传则由系统生成（SALE_+时间戳）")
    private String saleNo;

    @ApiModelProperty(value = "销售门店ID（shop_store.id），必填：明细候选按「商品属于这家门店且可销售」收窄")
    private Long storeId;

    @ApiModelProperty(value = "发货仓库ID（shop_erp_warehouse.id）：下单时可不填，出库时由出库明细那几行定")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long warehouseId;

    // 负责的 ERP 操作员：表单里那一格「ERP操作员」直接写这列，所以是 IGNORED 不是留痕列的 NEVER
    // （全局 not_empty 会把 null 整个从 SET 里跳过，清空就写不回去）。
    // ERP 账号（account_type=5）改不动：服务层钉成登录 id / 原值，同采购单
    @ApiModelProperty(value = "负责的ERP操作员用户ID（sys_user.id，account_type=5）；ERP账号按它认领自己那几条")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "销售总金额（服务端按明细累加，前端传了也不认）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "销售总数量（服务端按明细累加，前端传了也不认）")
    private Integer totalQuantity;

    @ApiModelProperty(value = "销售状态：0待审核 1已审核 2待出库 3已出库 4已取消（字典 sale_status；2/3 由出库进度回写，不是手工选的）")
    private Integer status;

    // 可空且表单里能清掉 → IGNORED（同 remark）；下面两个时间列故意不加：
    // 全局 not_empty 让"null 不进 SET"，服务端只在第一次进入该状态时补值，之后就抹不掉已记的时间
    @ApiModelProperty(value = "预计发货日期")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    private LocalDate expectDate;

    @ApiModelProperty(value = "审核时间（状态置为1时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime auditTime;

    @ApiModelProperty(value = "出库时间（状态置为3时服务端补，非表单字段）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime shipTime;

    @ApiModelProperty(value = "客户联系人")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactName;

    @ApiModelProperty(value = "客户联系电话")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactPhone;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用（同 ShopErpPurchase 那批冗余名称）
    @ApiModelProperty(value = "销售门店名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String storeName;

    @ApiModelProperty(value = "发货仓库名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String warehouseName;

    @ApiModelProperty(value = "ERP操作员昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "销售明细（新增/修改整体提交，查询详情时带出，非本表列）")
    @TableField(exist = false)
    private List<ShopErpSaleItem> items;

    @ApiModelProperty(value = "查询：单号/备注/门店名称关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：创建时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：创建时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String endTime;
}
