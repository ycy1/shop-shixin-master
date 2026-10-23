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
 * 仓库（进销存管理 -> 仓库管理）
 *
 * 编码列 NOT NULL 但不进表单：新增时由系统生成（见 ShopErpWarehouseServiceImpl#insert），
 * 写法照供应商 ShopErpSupplier。地区按项目统一口径只存两列：area_code 是 "110000,110100,110101"
 * 这种行政区划代码，area_zh 是解析出来的中文名。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_warehouse")
@ApiModel(value = "仓库")
public class ShopErpWarehouse extends BaseEntity {

    /** 状态：启用。唯一允许出入库、允许下单分仓挑中的那一档 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：禁用。只是不再让它出现在下拉候选里，已经压在单上的货照样能入出（见各 service 的仓库校验） */
    public static final int STATUS_DISABLED = 0;

    /** 状态：盘点中。账要停在这一刻才盘得准，所以入库、出库、下单分仓三处一律拦 */
    public static final int STATUS_STOCKTAKING = 2;

    /** 状态：调拨中。仓里的货正在往外挪，同一时间再出入一笔就说不清剩多少，拦法和盘点中一样 */
    public static final int STATUS_TRANSFER = 3;

    @ApiModelProperty(value = "仓库编码，唯一；新增时不传则由系统生成（WAREHOUSE_+时间戳）")
    private String warehouseCode;

    @ApiModelProperty(value = "仓库名称")
    private String warehouseName;

    @ApiModelProperty(value = "仓库类型：1主仓 2分仓 3门店仓（字典 warehouse_type）")
    private Integer warehouseType;

    // 可空列必须显式声明 IGNORED：全局 field-strategy=not_empty 会把 null 和空串一起从 UPDATE 的 SET 里跳过，
    // 否则前端清空联系人/电话/地址后保存不生效（下面这几个可空列同一处理）
    @ApiModelProperty(value = "关联门店ID（shop_store.id），门店仓时用；无物理外键")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long storeId;

    // 负责的操作员：表单里那一格「ERP操作员」直接写这列，所以是 IGNORED 不是留痕列的 NEVER
    // （全局 not_empty 会把 null 整个从 SET 里跳过，清空就写不回去），口径同采购单
    // ERP 账号（account_type=5）改不动：服务层钉成登录 id / 原值，列表也只按这一列收窄给他看
    @ApiModelProperty(value = "负责的ERP操作员用户ID（sys_user.id，account_type=5）；ERP 账号按它认领自己负责的仓库")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "联系人")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactName;

    @ApiModelProperty(value = "联系电话")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactPhone;

    @ApiModelProperty(value = "地区编码，逗号分隔的行政区划代码，必填；收货地址没坐标时统一下单按这一列匹仓")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaCode;

    @ApiModelProperty(value = "地区中文（由 area_code 解析出的冗余快照，列表直接显示这一列）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaZh;

    @ApiModelProperty(value = "详细地址（街道门牌，不含省市区）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String address;

    // 必填（服务层 validate 拦，前端 rules 同样必填）：统一下单按这两列就近分仓，
    // 没坐标的仓进不了距离那一档候选池（ShopErpInventoryMapper#selectNearestWarehouseId），
    // 只能落回按 area_code 匹仓的那一条
    @ApiModelProperty(value = "经度(GCJ-02)，必填，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度(GCJ-02)，必填，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal latitude;

    @ApiModelProperty(value = "仓库容量(件)，空表示不限")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer capacity;

    @ApiModelProperty(value = "容量预警值(件)，空表示不设预警线；默认取仓库容量的 10%（前端填容量时回填），不能大于容量")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer capacityWarn;

    // NOT NULL 且表单只读：新增由 normalize 补 0，修改时服务层钉回库里的值。
    // 唯一的写入方是 ShopErpWarehouseMapper#refreshCapacityUsed（按 shop_erp_inventory 现汇总重算），
    // 调它的是库存的两条记账路：入库和出库（ShopErpInventoryServiceImpl#inbound / #outbound 的末尾）
    @ApiModelProperty(value = "已用容量(件，冗余；服务端维护，前端传了也不认)")
    private Integer capacityUsed;

    @ApiModelProperty(value = "是否开启仓库预警：1是 0否")
    private Integer warnEnable;

    @ApiModelProperty(value = "状态：1启用 0禁用 2盘点中 3调拨中（字典 warehouse_status；只有 1 允许出入库和下单分仓，判据见 com.shop.utils.WarehouseUtils）")
    private Integer status;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用（同 ShopErpPurchase 的 supplierName / userName）
    @ApiModelProperty(value = "关联门店名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String storeName;

    @ApiModelProperty(value = "ERP操作员昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "查询：名称/编码/联系人/电话关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
