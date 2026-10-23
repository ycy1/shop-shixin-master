package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品规格表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_product_spec")
@ApiModel(value = "商品规格")
public class ShopProductSpec extends BaseEntity {

    // 可空且允许清空：全局是 not_empty，不加 IGNORED 的话页面上清了类目、UPDATE 根本不带这一列，库里还是老值
    @ApiModelProperty(value = "所属类目id，空=不限类目（对所有分类都是候选）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long categoryId;

    @ApiModelProperty(value = "规格名称")
    private String specName;

    @ApiModelProperty(value = "规格值")
    private String specValue;

    @ApiModelProperty(value = "规格单位")
    private String specUnit;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    // 创建人只在新增时写：NEVER 让这一列永不进 UPDATE 的 SET，表单里带什么都改不动
    @ApiModelProperty(value = "创建人用户ID")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    @ApiModelProperty(value = "所属类目名称（联表查询回填）")
    @TableField(exist = false)
    private String categoryName;

    @ApiModelProperty(value = "查询：规格名称/规格值关键字")
    @TableField(exist = false)
    private String keyword;
}
