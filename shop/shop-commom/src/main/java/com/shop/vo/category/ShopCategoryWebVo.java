package com.shop.vo.category;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 商品分类 Web 端视图（/api/web/shop/search/categories 专用）
 *
 * 不直出实体：后台那份额外带 icon、排序值、创建人 id 和一堆审计字段，前台筛选栏只要这三格。
 * 也不带 children —— 这一条接的是下拉框，服务端已经按「父在前、自己的下级紧跟其后」铺平了，
 * 缩进由页面按 level 做。（后台分类管理给的是整棵树，那是给运营改结构的，两回事。）
 */
@Data
@ApiModel(value = "商品分类 Web 端视图")
public class ShopCategoryWebVo {

    @ApiModelProperty(value = "分类id")
    private Long id;

    @ApiModelProperty(value = "分类名称")
    private String categoryName;

    @ApiModelProperty(value = "层级：1/2/3，页面按它给下拉项缩进")
    private Integer level;
}
