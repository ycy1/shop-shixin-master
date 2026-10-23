package com.shop.dto.refund;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 统一退货参数（「我的订单」列表上那颗「退货」按钮提交的这一份）
 *
 * 只收"退哪几行、各退几件、寄回哪个地址、为什么退"这四件事：
 * 退款金额由服务端按订单明细上的成交金额和小计算（单价、优惠都取下单时的快照），传了也不认；
 * 退款单号、申请时间、退款类型（全额/部分）也一律服务端定。
 *
 * 一次调用只落一张 sys_refund（status=0 待审核），不动订单、不动明细行、不动库存 ——
 * 那些等后台审核通过、退款回调成功时才做（见 ShopRefundCallbackServiceImpl）。
 * 所以这一格"退多少"是**申请**，审核人可以下调。
 */
@Data
@ApiModel(value = "统一退货参数")
public class ShopRefundCreateDto {

    @ApiModelProperty(value = "订单ID(sys_order.id)，和 orderNo 至少给一个；两个都给以 orderId 为准")
    private Long orderId;

    @ApiModelProperty(value = "订单号(sys_order.order_no)，没给 orderId 时按它找单")
    private String orderNo;

    @ApiModelProperty(value = "退货地址ID(shop_consumer_address.id)，选填：这一单要寄回买家名下哪条地址（上门取件/寄回都读它）。"
            + "必须是这一单下单人名下的地址；不传则回落到这一单当初的收货地址（sys_order.address_id）")
    private Long addressId;

    @ApiModelProperty(value = "退款原因（买家填的那一句），选填")
    private String refundReason;

    @ApiModelProperty(value = "退哪几行、各退几件，至少一行")
    private List<Item> items;

    /**
     * 一行 = 一张订单明细行里的几件；同一商品分成几行（不同规格）就是几条，不按 productId 合并
     */
    @Data
    @ApiModel(value = "退货明细行")
    public static class Item {

        @ApiModelProperty(value = "订单明细行ID(shop_order_item.id)，必须是这一单名下的行")
        private Long orderItemId;

        @ApiModelProperty(value = "这一行要退几件，大于 0，且不超过该行的可退数（购买数量 − 已退货数量）")
        private Integer quantity;
    }
}
