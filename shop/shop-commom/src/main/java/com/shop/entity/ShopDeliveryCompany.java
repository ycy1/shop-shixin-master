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
 * 物流公司表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_delivery_company")
@ApiModel(value = "物流公司")
public class ShopDeliveryCompany extends BaseEntity {

    @ApiModelProperty(value = "物流公司编码，唯一；新增时不传则由系统生成（LOGISTICS_+时间戳）")
    private String companyCode;

    @ApiModelProperty(value = "物流公司名称，唯一")
    private String companyName;

    // 可空列必须显式声明 IGNORED：全局 field-strategy=not_empty 会把 null 和空串一起从 UPDATE 的 SET 里跳过，
    // 否则前端清空简称/客服电话/官网/LOGO 后保存不生效
    @ApiModelProperty(value = "简称，如 顺丰")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String companyShort;

    @ApiModelProperty(value = "类型：1快递 2物流 3同城配送 4国际（字典 logistics_company_type）")
    private Integer companyType;

    @ApiModelProperty(value = "客服电话")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String servicePhone;

    @ApiModelProperty(value = "官网")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String website;

    @ApiModelProperty(value = "LOGO URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String logo;

    @ApiModelProperty(value = "公司简介")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;

    @ApiModelProperty(value = "是否支持货到付款：1是 0否")
    private Integer supportCod;

    @ApiModelProperty(value = "是否支持保价：1是 0否")
    private Integer supportInsurance;

    @ApiModelProperty(value = "是否支持易碎品：1是 0否")
    private Integer supportFragile;

    @ApiModelProperty(value = "最大承重(kg)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal maxWeight;

    @ApiModelProperty(value = "配送范围说明")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String deliveryArea;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：1启用 0禁用（字典 logistics_status）")
    private Integer status;

    // 创建人只在新增时写：NEVER 让这一列永不进 UPDATE 的 SET，表单里带什么都改不动。
    // 物流公司是全局共享数据，这一列只留痕，列表和写操作都不按它拦（和分类、图标库同一处理）
    @ApiModelProperty(value = "创建人用户ID（只留痕，不参与数据范围过滤）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    @ApiModelProperty(value = "查询：名称/编码/简称关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：支持服务，逗号分隔的 cod/insurance/fragile，勾中的几项支持任意一项即命中（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String supportServices;
}
