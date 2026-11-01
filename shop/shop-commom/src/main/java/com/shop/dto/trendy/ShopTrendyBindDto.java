package com.shop.dto.trendy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 潮玩系列绑定商品
 *
 * 一次可以处理一批（商品选择弹窗是多选档，确定时抛一批上来），所以这里收的是 id 集合。
 * 只有「绑到哪一条系列 / 解绑」这一件事，不收整个商品实体 ——
 * 走 ShopProductSaveDto 那条口会把 SKU、轮播图那一整套重存一遍，改一列不该动那么多东西。
 *
 * @author xxj
 */
@Data
@ApiModel(value = "潮玩系列绑定商品")
public class ShopTrendyBindDto {

    @ApiModelProperty(value = "商品id集合(shop_product.id)")
    private List<Long> productIds;

    /** 传 null 就是解绑（这一格本来就是可空的）；前端解绑时显式带 null 过来 */
    @ApiModelProperty(value = "潮玩系列id(shop_trendy.id)，NULL=解绑")
    private Long trendyId;
}
