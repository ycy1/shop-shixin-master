package com.shop.vo.order;

import com.shop.entity.SysOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 我的订单一行（一张 sys_order）的视图对象
 *
 * 列就是主表那几列（订单号 / 下单人 / 金额 / 状态 / 支付信息 / 备注），另外三样是明细行上汇总出来的：
 * 行数、件数、名下门店名。这三个不是 sys_order 的列，是查询时按 order_id 现算的子查询，
 * 只出现在 VO 里 —— 明细本身不落冗余，一有出入就是两处对不上（口径同出库单主表的 total_quantity）。
 * deliveryId / deliveryNo / deliveryStatus 是第四、五、六样：配送单在**统一下单**那一刻就跟着这张订单建好了
 * （下单的接口里直接带 order_id，见 shop-api 的 ShopOrderDeliveryService），所以没支付也能看见自己那一单。
 * refundedQuantity / pendingRefundCount 跟着一块儿回来，一个从明细汇总已退件数、一个数在途退款单张数
 * （2026-09-22 起它不再拦退货：一张订单可以同时挂好几张在途单，额度按行占）；
 * refundCount 是另一颗「退款单」按钮的判据（这一单名下全部退款单，点开看每次各退了哪几行）。
 * items 只有详情接口填，列表接口不带（分页时不做 N+1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "我的订单（主表 + 明细）视图对象")
public class ShopOrderVo extends SysOrder {

    @ApiModelProperty(value = "商品行数（联查 shop_order_item 汇总，非 sys_order 列）")
    private Integer itemCount;

    @ApiModelProperty(value = "商品件数合计（同上，各行购买数量相加）")
    private Integer totalQuantity;

    @ApiModelProperty(value = "名下门店名称（各行 store_id 去重拼接，非门店的商品行显示「非门店商品」）")
    private String storeNames;

    // 这三个不是列，是现查 shop_delivery 带出来的（配送单表上没有单独的订单列，
    // 那一槽在换绑之前装的就是这一单的 id + 订单号，所以待支付那几行也查得到，口径见 ShopOrderItemMapper.xml）
    // 列表靠 deliveryId 决定给不给看「配送单」那颗按钮（下单就有，所以待支付那几行也给看），
    // deliveryStatus 用来在那张弹窗上说清这一单派到哪一步了，口径同商品出库列表上那三格
    @ApiModelProperty(value = "这一单的配送单ID（shop_delivery.id，统一下单时就建；NULL=老订单没补建）")
    private Long deliveryId;

    @ApiModelProperty(value = "配送单号（同上，只用来把提示说清楚）")
    private String deliveryNo;

    @ApiModelProperty(value = "配送单状态（同上，字典 delivery_status：0待派单 1待接单 2已接单 3配送中 4已送达 5已取消 6异常）")
    private Integer deliveryStatus;

    // 这两格都是「退货」那一块的附属信息，都由 XML 里的子查询现算（判据不给前端猜）：
    // refundedQuantity = 名下各行已退件数合计（钱已经退出去的那一份）；pendingRefundCount = 在途退款单张数
    // （0待审核 / 3退款中），2026-09-22 起它**不拦退货**，只在弹窗上说明"为什么还能退的比总数少"，
    // 真正压住可退数的是明细行上那一格 refundingQuantity（按行占的额度，见 ShopOrderItemVo）
    @ApiModelProperty(value = "已退货件数合计（联查 shop_order_item.returned_quantity 汇总，非 sys_order 列）")
    private Integer refundedQuantity;

    @ApiModelProperty(value = "在途退款单条数（sys_refund 里 status ∈ 0/3 的张数，非 sys_order 列；只用于提示，不拦退货）")
    private Integer pendingRefundCount;

    // 列表那颗「退款单」按钮的判据：这一单名下**全部**退款单张数（不分状态，被拒/失败/取消的也算）。
    // 和上面那格不是一回事：这一格要的是"有过退货就点得开"，那一格只数现在还在流程里的
    @ApiModelProperty(value = "退款单总张数（sys_refund 名下全部，非 sys_order 列；>0 时列表给看「退款单」按钮）")
    private Integer refundCount;

    @ApiModelProperty(value = "商品明细行（只有详情接口带）")
    private List<ShopOrderItemVo> items;
}
