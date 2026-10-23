package com.shop.vo.favorite;

import com.shop.entity.ShopConsumerFavorite;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收藏列表/详情视图对象
 *
 * target_id 按 targetType 分别指向三张表，所以对象名称是 XML 里用 CASE 从对应联表里挑出来的，
 * 消费者昵称来自 sys_user，两者都不是 shop_consumer_favorite 的列，只出现在 VO 里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "收藏视图对象")
public class ShopFavoriteVo extends ShopConsumerFavorite {

    @ApiModelProperty(value = "收藏对象名称（按类型取商品名/门店名/品牌名，对象已删除时为空）")
    private String targetName;

    @ApiModelProperty(value = "消费者昵称（联表带出）")
    private String nickname;

    @ApiModelProperty(value = "消费者用户名（联表带出）")
    private String username;
}
