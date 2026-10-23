package com.shop.vo.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 商品规格 Web 端视图（详情页那一条）
 *
 * 不直接吐 ShopProductSpec 那个实体：它继承 BaseEntity，一起回给不登录的前台就是
 * 把「这一条是谁建的、什么时候改过、挂在哪个类目、启没启用」全带出去了（口径同 ShopProductWebVo 头部那段）。
 * 这一份只留页面上要印出来的三格。
 */
@Data
@ApiModel(value = "商品规格 Web 端视图")
public class ShopProductSpecWebVo {

    @ApiModelProperty(value = "规格id")
    private Long id;

    @ApiModelProperty(value = "规格名称")
    private String specName;

    @ApiModelProperty(value = "规格值")
    private String specValue;

    @ApiModelProperty(value = "规格单位")
    private String specUnit;
}
