package com.shop.vo.trendy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 盲盒款式（盒里可能抽到的那一款）Web 端视图 —— 只给卡片上那一排小图要用的几格。
 *
 * 概率（probability）跟着走是因为「是否公示概率」这一格由盒自己配置：
 * 盒上勾了公示（isProbabilityShow=1）页面才渲染百分比，没勾就不显示（合规那一刀在页面上按盒的开关走）。
 * skuId、绑定的商品这些后台配置不外给。
 */
@Data
@ApiModel(value = "盲盒款式 Web 端视图")
public class ShopTrendyBoxItemWebVo {

    @ApiModelProperty(value = "所属盲盒id（一次批量查多个盒时用它归堆）")
    private Long boxId;

    @ApiModelProperty(value = "款式名称")
    private String styleName;

    @ApiModelProperty(value = "款式图 URL")
    private String styleImage;

    @ApiModelProperty(value = "是否隐藏款：1是 0否")
    private Integer isHidden;

    @ApiModelProperty(value = "抽中概率（小数），盒上没勾公示时页面不显示")
    private BigDecimal probability;

    @ApiModelProperty(value = "剩余数量，NULL=不限")
    private Integer remainCount;
}
