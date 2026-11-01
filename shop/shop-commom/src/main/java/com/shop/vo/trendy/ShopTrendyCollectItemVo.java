package com.shop.vo.trendy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集齐进度弹窗里点开一个人之后的那一排格子：一条潮玩系列下的<b>全部</b>规格，
 * 每个格子上标出这个人抽到过没有（抽到过的那一格带抽到的时间）。
 *
 * 一行一个规格、不是"他抽到的那些记录" —— 差哪几个只有从整套这一侧才看得出来，
 * 反过来列他抽过的款式，页面还得自己数「3 / 5」里的分母是哪来的。
 * 所以这一条查询以 shop_product_sku 打头，抽取记录是 LEFT JOIN 上来的（同上面那个进度列表的分子口径：
 * 同一种规格抽到几次都只占一格，取最早抽到的那一天给人看）。
 */
@Data
@ApiModel(value = "潮玩系列集齐进度·款式明细")
public class ShopTrendyCollectItemVo {

    @ApiModelProperty(value = "规格ID(shop_product_sku.id)")
    private Long skuId;

    @ApiModelProperty(value = "所属商品名称")
    private String productName;

    @ApiModelProperty(value = "规格文本（如「隐藏款」「常规款」，没配规格是空）")
    private String specText;

    @ApiModelProperty(value = "规格图（没配图由页面回落到商品图/占位）")
    private String skuImage;

    @ApiModelProperty(value = "商品ID(shop_product.id)，页面按它分组用")
    private Long productId;

    @ApiModelProperty(value = "这个人第一次抽到这一格的时间，NULL=还没抽到过")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime drawnTime;
}
