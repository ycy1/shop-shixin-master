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
 * 供应商（进销存管理 -> 供应商管理）
 *
 * 编码列 NOT NULL 但不进表单：新增时由系统生成（见 ShopErpSupplierServiceImpl#insert）。
 * 地区按项目统一口径只存两列：area_code 是 "110000,110100,110101" 这种行政区划代码，
 * area_zh 是解析出来的中文名（同 sys_user / shop_store）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_supplier")
@ApiModel(value = "供应商")
public class ShopErpSupplier extends BaseEntity {

    @ApiModelProperty(value = "供应商编码，唯一；新增时不传则由系统生成（SUPPLIER_+时间戳）")
    private String supplierCode;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    // 可空列必须显式声明 IGNORED：全局 field-strategy=not_empty 会把 null 和空串一起从 UPDATE 的 SET 里跳过，
    // 否则前端清空联系人/电话/地址后保存不生效（下面这几个可空列同一处理）
    @ApiModelProperty(value = "联系人")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactName;

    @ApiModelProperty(value = "联系电话")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactPhone;

    @ApiModelProperty(value = "地区编码，逗号分隔的行政区划代码")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaCode;

    @ApiModelProperty(value = "地区中文（由 area_code 解析出的冗余快照，列表直接显示这一列）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaZh;

    @ApiModelProperty(value = "详细地址（街道门牌，不含省市区）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String address;

    @ApiModelProperty(value = "经度(GCJ-02)，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度(GCJ-02)，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal latitude;

    @ApiModelProperty(value = "开户行")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String bankName;

    @ApiModelProperty(value = "银行账号")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String bankAccount;

    @ApiModelProperty(value = "税号")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String taxNo;

    @ApiModelProperty(value = "状态：1启用 0禁用（字典 supplier_status）")
    private Integer status;

    // 绑定账号：表单里那个「绑定账号」选择器直接写这一列，所以不是 NEVER 了。
    // 用 IGNORED 是为了"清空绑定"能写回 NULL（全局 not_empty 会把 null 整个从 SET 里跳过）。
    // 供应商账号（account_type=4）改不动：服务层把它的值钉成登录 id
    @ApiModelProperty(value = "绑定账号的用户ID；供应商账号(account_type=4)即靠它认领自己那条记录")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    // 昵称不是 shop_erp_supplier 的列，服务层按 user_id 查 sys_user 回填，
    // 给列表显示和绑定账号选择器回显用（同 ShopStore.managerName）
    @ApiModelProperty(value = "绑定账号昵称（服务层回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "查询：名称/编码/联系人/电话关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
