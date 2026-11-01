package com.shop.dto.trendy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 潮玩IP绑定系列
 *
 * 一次可以处理一批（系列选择弹窗是多选档，确定时抛一批上来），所以收 id 集合。
 * 只动 shop_trendy.ip_id 这一列，不收整个系列实体 —— 口径同 ShopTrendyBindDto。
 *
 * @author xxj
 */
@Data
@ApiModel(value = "潮玩IP绑定系列")
public class ShopTrendyIpBindDto {

    @ApiModelProperty(value = "潮玩系列id集合(shop_trendy.id)")
    private List<Long> trendyIds;

    /** 传 null 就是解绑（这一格本来就是可空的）；前端解绑时显式带 null 过来 */
    @ApiModelProperty(value = "潮玩IP id(shop_trendy_ip.id)，NULL=解绑")
    private Long ipId;
}
