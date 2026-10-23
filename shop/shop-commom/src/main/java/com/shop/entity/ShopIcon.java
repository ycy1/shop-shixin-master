package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图标库表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_icon")
@ApiModel(value = "图标库")
public class ShopIcon extends BaseEntity {

    @ApiModelProperty(value = "图标名称")
    private String iconName;

    @ApiModelProperty(value = "图标编码，唯一")
    private String iconCode;

    @ApiModelProperty(value = "图标类型：1 SVG 2 图片URL 3 字体类名")
    private Integer iconType;

    @ApiModelProperty(value = "图标内容：SVG 源码 / 图片URL / 字体类名")
    private String iconValue;

    // 可空列必须显式声明 IGNORED：全局 field-strategy=not_empty 会把 null 从 UPDATE 的 SET 里整个跳过，
    // 否则前端清空后无法保存
    @ApiModelProperty(value = "商品分类id")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long categoryId;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 创建人只在新增时写：NEVER 让这一列永不进 UPDATE 的 SET，表单里带什么都改不动
    @ApiModelProperty(value = "创建人用户ID")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    @ApiModelProperty(value = "所属类目名称（联表查询回填）")
    @TableField(exist = false)
    private String categoryName;

    @ApiModelProperty(value = "查询：图标名称/编码关键字")
    @TableField(exist = false)
    private String keyword;
}
