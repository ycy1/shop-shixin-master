package com.shop.vo.delivery;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 取货 / 送达 / 取消这一步之后的那几格
 *
 * 和接单的返回同理：客户端拿到就能提示"已取货 / 已送达 / 已取消"，不用再调一次详情。
 * 三颗接口共用这一个形状，所以里面有两格只在其中一路有值（见那两格自己的注释）。
 */
@Data
@ApiModel(value = "配送单取货/送达/取消结果")
public class ShopDeliveryActionVo {

    @ApiModelProperty(value = "配送单ID(shop_delivery.id)")
    private Long deliveryId;

    @ApiModelProperty(value = "配送单号")
    private String deliveryNo;

    @ApiModelProperty(value = "这一步之后的配送状态：3 配送中 / 4 已送达 / 5 已取消（字典 delivery_status）")
    private Integer status;

    @ApiModelProperty(value = "这一单的送货员ID(shop_delivery_rider.id)；取消一张还没人接的单时为空")
    private Long riderId;

    @ApiModelProperty(value = "这一单的送货员姓名（服务端按上面那个 id 现查）；没派人时为空")
    private String riderName;

    @ApiModelProperty(value = "这一步写进去的时间：取货=pickup_time，送达=delivered_time，取消=cancel_time（服务端取的当前时间）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime actionTime;

    @ApiModelProperty(value = "取消原因：只有取消那一路非空")
    private String cancelReason;

    @ApiModelProperty(value = "取消时顺带从「已发货」退回「已出库」的订单明细行数（ship_status 1 → 3）；"
            + "取货和送达这两步不动明细行，所以那两步回来是 null —— 前端按 null 不显示这一格")
    private Integer revertedItemCount;
}
