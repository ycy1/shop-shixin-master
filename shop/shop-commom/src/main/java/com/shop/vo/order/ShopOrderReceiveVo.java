package com.shop.vo.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 确认收货的结果
 *
 * 只回"这一单现在是哪一档 + 什么时候收的"，客户端拿到就能提示"已确认收货"，不用再调一次详情。
 * 整单信息（金额、明细行那些）还是走 /shop/orderItem/order/{id}。
 */
@Data
@ApiModel(value = "订单确认收货结果")
public class ShopOrderReceiveVo {

    @ApiModelProperty(value = "订单ID(sys_order.id)")
    private Long orderId;

    @ApiModelProperty(value = "订单号")
    private String orderNo;

    @ApiModelProperty(value = "确认收货后的订单状态：固定 2 已完成（字典 order_status）")
    private Integer status;

    // 表上没有单独的收货时间列（sys_order 只有 pay_time 那一格是终局时间），所以这一格是服务端
    // 写状态那一刻取的当前时间，只回给调用方看，不落库
    @ApiModelProperty(value = "确认收货的时间（服务端取的当前时间；sys_order 没有收货时间列，这一格不落库）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime receiveTime;

    @ApiModelProperty(value = "这一单的明细行数（判据就是看这几行是不是全都「已发货」，回显让人数得清）")
    private Integer itemCount;
}
