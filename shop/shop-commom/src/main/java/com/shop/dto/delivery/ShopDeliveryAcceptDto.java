package com.shop.dto.delivery;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 配送单接单参数（送货员把某一单揽到自己名下，后台也可以替某位送货员确认接单）
 *
 * 只收"接哪一单、由哪位送货员接"这两件事：
 * 接单时间由服务端取当前时间，预计配送时长和预计送达时间是服务端按那位送货员的交通工具算出来的，
 * 状态、单号、距离、配送费一律不认参数里传的东西（传了也不认）。
 *
 * 送货员账号（account_type=2）传 riderId 是无效的：那一档一律按他自己的送货员档案处理，
 * 口径同 /shop/rider/my（自助接口只作用于调用者自己那一行），伪造别人的 id 也接不走别人的单。
 */
@Data
@ApiModel(value = "配送单接单参数")
public class ShopDeliveryAcceptDto {

    @ApiModelProperty(value = "配送单ID(shop_delivery.id)，必填")
    private Long deliveryId;

    @ApiModelProperty(value = "接单的送货员ID(shop_delivery_rider.id)：后台账号必填（等于替他确认接单），"
            + "送货员账号不用传、传了也不认")
    private Long riderId;
}
