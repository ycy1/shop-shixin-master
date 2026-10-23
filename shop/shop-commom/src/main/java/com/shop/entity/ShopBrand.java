package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_brand")
@ApiModel(value = "品牌")
public class ShopBrand extends BaseEntity {

    @ApiModelProperty(value = "品牌名称，唯一")
    private String brandName;

    // 可空列必须显式声明 IGNORED：全局 field-strategy=not_empty 会把 null 和空串一起从 UPDATE 的 SET 里跳过，
    // 否则前端清空 LOGO/介绍/联系方式/分类后保存不生效
    @ApiModelProperty(value = "品牌LOGO URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String logo;

    // shop_brand.brand_spokesperson 是 json 列，按项目约定映射成 String（同 ShopProduct.images）。
    // 这个字段不能加 IGNORED：全局 not_empty 跳过空串，正好挡住了空串写进 json 列直接报错；
    // "清空代言人"是前端提交 "[]" 实现的，服务层还会把 blank 归一成 "[]"。
    @ApiModelProperty(value = "代言人图片，JSON 数组字符串：[{\"name\":\"\",\"url\":\"\"}]")
    private String brandSpokesperson;

    @ApiModelProperty(value = "品牌介绍")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;

    @ApiModelProperty(value = "联系方式")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contact;

    @ApiModelProperty(value = "所属分类id")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long categoryId;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    // 创建人只在新增时写：NEVER 让这一列永不进 UPDATE 的 SET，表单里带什么都改不动
    @ApiModelProperty(value = "创建人用户ID")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;
}
