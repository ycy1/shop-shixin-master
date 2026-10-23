package com.shop.dto.order;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 订单主表分页查询参数（后台「消费者管理 → 我的订单」列表，一行是一张订单）
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 *
 * 发货状态、所属门店这两个筛选本来就是明细行上的事（shop_order_item 的列），
 * 在订单级的列表上含义是"名下有这样一个商品行"，用 EXISTS 表达，见 ShopOrderItemMapper.xml。
 */
@Data
@ApiModel(value = "我的订单（主表）查询参数")
public class ShopOrderQueryDto {

    @ApiModelProperty(value = "关键字：订单号/下单人/手机号")
    private String keyword;

    @ApiModelProperty(value = "下单消费者ID（消费者账号登录时后端会覆盖为本人 id）")
    private Long userId;

    @ApiModelProperty(value = "门店ID（匹配名下有该门店商品的订单）")
    private Long storeId;

    @ApiModelProperty(value = "发货状态：0待发货 3已出库 1已发货 4部分退货 2已退货（匹配名下有该状态行的订单）")
    private Integer shipStatus;

    @ApiModelProperty(value = "订单状态：0待支付 1已支付 2已完成 3已取消 4已退款 5已关闭（字典 order_status）")
    private Integer status;

    @ApiModelProperty(value = "下单时间范围-开始")
    private String beginTime;

    @ApiModelProperty(value = "下单时间范围-结束")
    private String endTime;
}
