package com.shop.vo.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.ShopOrderItem;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细视图对象（详情弹窗下面那张商品表格的一行）
 *
 * 商品编码、门店名、仓库名、消费者昵称都是联表带出的；订单本身的状态和支付信息在 sys_order 上，
 * 这一行只带自己那份快照，所以也要联。这些都不是 shop_order_item 的列，只出现在 VO 里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "订单明细视图对象")
public class ShopOrderItemVo extends ShopOrderItem {

    @ApiModelProperty(value = "商品编码（联表带出，本表没冗余这一列）")
    private String productCode;

    /**
     * 这一行规格所属的那件商品。本表只有 sku_id，这一格是联到 shop_product 那一跳顺手带的 ——
     * 前台「我的订单」要点回商品详情页，那个路由认的是商品 id，规格 id 点不进去。
     */
    @ApiModelProperty(value = "所属商品ID（由 sku_id 联 shop_product_sku 带出）")
    private Long productId;

    @ApiModelProperty(value = "门店名称（联表带出）")
    private String storeName;

    @ApiModelProperty(value = "发货仓库名称（联表带出）")
    private String warehouseName;

    @ApiModelProperty(value = "下单消费者昵称（联表带出）")
    private String nickname;

    @ApiModelProperty(value = "下单消费者用户名（联表带出）")
    private String username;

    /**
     * 这一行被**在途退款单**（0 待审核 / 3 退款中）占掉的件数。不是列，是服务层现算的：
     * 一张订单可以同时挂好几张退款单，所以"还能退几件" = quantity − returned_quantity − 这一格，
     * 和 ShopRefundCreateServiceImpl 建单时那道行级闸同口径（前端弹窗照这一格算 max）
     */
    @ApiModelProperty(value = "在途退款单已占掉的件数（服务层按各张 sys_refund.extra 现算，非本表列）")
    private Integer refundingQuantity;

    @ApiModelProperty(value = "所属订单状态：0待支付 1已支付 2已完成 3已取消 4已退款 5已关闭（字典 order_status，取自 sys_order）")
    private Integer orderStatus;

    @ApiModelProperty(value = "所属订单支付方式（取自 sys_order，字典 pay_type）")
    private Integer orderPayType;

    @ApiModelProperty(value = "所属订单实付金额（取自 sys_order）")
    private BigDecimal orderPayAmount;

    @ApiModelProperty(value = "所属订单支付时间（取自 sys_order，NULL=还没付）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime orderPayTime;

    @ApiModelProperty(value = "所属订单备注（取自 sys_order，一般是买家留言）")
    private String orderRemark;
}
