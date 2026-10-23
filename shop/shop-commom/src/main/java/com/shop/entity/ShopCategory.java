package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 商品类目表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_category")
@ApiModel(value = "商品类目")
public class ShopCategory extends BaseEntity {

    @ApiModelProperty(value = "父类目ID，0为根")
    private Long parentId;

    @ApiModelProperty(value = "类目名称")
    private String categoryName;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "层级：1/2/3")
    private Integer level;

    @ApiModelProperty(value = "图标，存 shop_icon.id")
    private String icon;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    // 创建人只在新增时写：NEVER 让这一列永不进 UPDATE 的 SET，表单里带什么都改不动
    @ApiModelProperty(value = "创建人用户ID")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    @ApiModelProperty(value = "图标名称（联表 shop_icon 回填）")
    @TableField(exist = false)
    private String iconName;

    @ApiModelProperty(value = "图标类型：1 SVG 2 图片URL 3 字体类名（联表 shop_icon 回填）")
    @TableField(exist = false)
    private Integer iconType;

    @ApiModelProperty(value = "图标内容（联表 shop_icon 回填）")
    @TableField(exist = false)
    private String iconValue;

    @ApiModelProperty(value = "子类目")
    @TableField(exist = false)
    private List<ShopCategory> children;

    @ApiModelProperty(value = "查询：类目名称关键字")
    @TableField(exist = false)
    private String keyword;
}
