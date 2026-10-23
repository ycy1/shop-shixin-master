package com.shop.dto.delivery;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 配送单后三步（取货 / 送达 / 取消）的参数 —— 三颗接口共用一个入参形状
 *
 * 只收"动哪一单"，取消那一路再多收一句原因：
 * 状态、时间（pickup_time / delivered_time / cancel_time）一律由服务端按当前时间写，
 * 参数里传 status、传时间都不认。
 *
 * 这里**没有** riderId，和接单那个入参的差别是有意的：接单那一刻单上还没有人，所以必须由调用方点名
 * 由谁接（送货员账号那一档除外）；取货及以后的三步，人已经写在配送单上了，再收一个 riderId
 * 就等于给"把别人名下那一单标成已送达"开后门。谁能动哪一单，见 ShopDeliveryAcceptServiceImpl 里
 * 那一句 assertRunningBy。
 */
@Data
@ApiModel(value = "配送单取货/送达/取消参数")
public class ShopDeliveryActionDto {

    @ApiModelProperty(value = "配送单ID(shop_delivery.id)，必填")
    private Long deliveryId;

    @ApiModelProperty(value = "取消原因：只有取消那一路会读，其它两步传了也不认；不填时服务端兜底一句默认原因")
    private String cancelReason;
}
