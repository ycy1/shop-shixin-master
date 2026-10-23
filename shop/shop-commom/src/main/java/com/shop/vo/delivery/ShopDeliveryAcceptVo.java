package com.shop.vo.delivery;

import com.shop.utils.DateUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接单的结果：这一单现在归谁、到第几步了，以及接单这一步顺带算出来的那两个时间
 *
 * 给的是"接完单之后这一单的关键几格"，客户端拿到就能提示"已接单，预计 xx 送达"，
 * 不用再调一次详情。整单信息（收件人、费用那些）还是走 /shop/delivery/{id}。
 */
@Data
@ApiModel(value = "配送单接单结果")
public class ShopDeliveryAcceptVo {

    @ApiModelProperty(value = "配送单ID(shop_delivery.id)")
    private Long deliveryId;

    @ApiModelProperty(value = "配送单号")
    private String deliveryNo;

    @ApiModelProperty(value = "接单后的配送状态：固定 2 已接单（字典 delivery_status）")
    private Integer status;

    @ApiModelProperty(value = "接单的送货员ID(shop_delivery_rider.id)")
    private Long riderId;

    @ApiModelProperty(value = "接单的送货员姓名")
    private String riderName;

    @ApiModelProperty(value = "接单时间（服务端取的当前时间）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime acceptTime;

    @ApiModelProperty(value = "预计配送时长(分钟，按这位送货员的交通工具折的)；NULL=这一单两个地点凑不齐、算不出距离，所以估不出时长")
    private Integer estimatedTime;

    @ApiModelProperty(value = "预计送达时间 = 接单时间 + 预计时长；上一格为空时这格也为空")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime expectTime;

    @ApiModelProperty(value = "接单这一步顺带标成「已发货」的订单明细行数（shop_order_item.ship_status 3 已出库 → 1 已发货）；"
            + "0 表示这一单名下的商品行还没有一行是「已出库」的（货还没出满，不该算发出去）")
    private Integer shippedItemCount;
}
