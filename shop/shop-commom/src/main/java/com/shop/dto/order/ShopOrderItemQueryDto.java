package com.shop.dto.order;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 订单明细分页查询参数（后台「消费者管理 → 我的订单」）
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 */
@Data
@ApiModel(value = "订单明细查询参数")
public class ShopOrderItemQueryDto {

    @ApiModelProperty(value = "商品名称/订单号关键字")
    private String keyword;

    @ApiModelProperty(value = "下单消费者ID（消费者账号登录时后端会覆盖为本人 id）")
    private Long userId;

    @ApiModelProperty(value = "所属订单ID")
    private Long orderId;

    @ApiModelProperty(value = "门店ID")
    private Long storeId;

    @ApiModelProperty(value = "发货状态：0待发货 3已出库 1已发货 4部分退货 2已退货（字典 order_ship_status）")
    private Integer shipStatus;

    @ApiModelProperty(value = "所属订单状态（sys_order.status，字典 order_status）")
    private Integer orderStatus;

    @ApiModelProperty(value = "下单时间范围-开始")
    private String beginTime;

    @ApiModelProperty(value = "下单时间范围-结束")
    private String endTime;
}
