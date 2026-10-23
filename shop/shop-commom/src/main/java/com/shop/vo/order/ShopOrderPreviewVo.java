package com.shop.vo.order;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 下单试算的结果（结算页用：还没提交，只把这一单的钱报给买家看）
 *
 * 和统一下单返回的那一份（ShopOrderCreateVo）的区别只是"细"：这里多了逐张券、逐行商品的抵扣，
 * 但**每一个数都由同一段计算得出**（见 ShopOrderCreateServiceImpl#quote），所以这一单真提交之后
 * 收的钱就是这里报的数，不会因为试算另算一遍而出现第二种说法。
 *
 * 金额之间的账是平的：
 * - totalAmount = goodsAmount + deliveryFee（要收的钱 = 货 + 运费）
 * - discountAmount = goodsDiscount + shippingDiscount（货值券抵的 + 免邮券抵的，只记券）
 * - payAmount = totalAmount − discountAmount − pointsDiscount（买家实付，积分那一份单独扣、不混进券的营销账）
 * - Σ items.paidAmount = goodsAmount − goodsDiscount − pointsDiscount（货值实付，"实付满多少免配送费"那一档看的就是它）
 *
 * 没做的事：不落库、不锁库存、不核销券、不动积分账户（usedPoints 那一格只是"提交后要冻这么多分"）——
 * 试算只是问一遍，买家点提交那一刀才算数。
 */
@Data
@ApiModel(value = "下单试算结果")
public class ShopOrderPreviewVo {

    @ApiModelProperty(value = "商品行数")
    private Integer itemCount;

    @ApiModelProperty(value = "货值合计 = Σ 各行小计（原价口径，单价取自商品表）")
    private BigDecimal goodsAmount;

    @ApiModelProperty(value = "配送费（按当时优先级最高的启用规则算，还没减免邮券）")
    private BigDecimal deliveryFee;

    @ApiModelProperty(value = "优惠券抵扣合计 = 货值券 + 免邮券")
    private BigDecimal discountAmount;

    @ApiModelProperty(value = "其中：货值券（满减/折扣/代金）抵掉的货钱，已经摊到各行 discount_amount 上")
    private BigDecimal goodsDiscount;

    @ApiModelProperty(value = "其中：免邮券抵掉的配送费（不落明细行，只减这一单的应付）")
    private BigDecimal shippingDiscount;

    @ApiModelProperty(value = "这一单用掉的积分合计 = Σ 各行 used_points（提交后冻进账户的 frozen_points，支付成功才真扣走）")
    private Integer usedPoints;

    @ApiModelProperty(value = "这些积分抵掉多少钱（按 sys_config 的 points_exchange 折，不进 discountAmount）")
    private BigDecimal pointsDiscount;

    @ApiModelProperty(value = "应付总额 = 货值合计 + 配送费")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "预计实付 = 应付总额 − 优惠合计 − 积分抵扣（这就是提交后要支付的数）")
    private BigDecimal payAmount;

    @ApiModelProperty(value = "逐张券抵了多少，顺序就是请求体里 couponIds 的处理顺序")
    private List<Coupon> coupons;

    @ApiModelProperty(value = "逐行商品的优惠（券是按适用范围摊到行上的，所以每一行抵了多少只有服务端知道）")
    private List<Item> items;

    /**
     * 一张券在这一单里抵了多少。
     *
     * used = false 只可能是免邮券：这一单配送费本来就是 0（没配启用规则，或货值实付够上了"免配送费"那一档），
     * 或者同一单里已经有另一张免邮券消掉了那一笔运费（一趟配送只消一张）。
     * 这种券不核销、留在买家手上，下次下单还能用；其余券既然判过了就一定会用掉。
     */
    @Data
    @ApiModel(value = "试算的单张优惠券抵扣")
    public static class Coupon {

        @ApiModelProperty(value = "用户券ID(shop_activity_coupon_user.id)")
        private Long couponId;

        @ApiModelProperty(value = "券名称（用户券上的快照）")
        private String couponName;

        @ApiModelProperty(value = "券类型：1满减券 2折扣券 3免邮券 4代金券（字典 coupon_type，也是快照）")
        private Integer couponType;

        @ApiModelProperty(value = "这一单里这张券抵了多少（used=false 的那张是 0）")
        private BigDecimal discountAmount;

        @ApiModelProperty(value = "这一单会不会真用掉这张券：true 提交就核销，false 不核销、还留在账户里")
        private Boolean used;
    }

    /**
     * 一行规格的试算结果：单价和行小计都是服务端按 SKU 表算的，行优惠是券摊下来的，
     * 积分是请求体里这一行自己报的、抵扣金额由服务端折
     */
    @Data
    @ApiModel(value = "试算的明细行")
    public static class Item {

        @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)")
        private Long skuId;

        @ApiModelProperty(value = "商品名称（取自商品表，不是客户端传的）")
        private String productName;

        @ApiModelProperty(value = "规格文本（取自 shop_product_sku.spec_text，不是客户端传的）")
        private String specText;

        @ApiModelProperty(value = "单价（shop_product_sku.product_price）")
        private BigDecimal price;

        @ApiModelProperty(value = "数量")
        private Integer quantity;

        @ApiModelProperty(value = "这一行的小计 = 单价 × 数量（原价口径）")
        private BigDecimal totalAmount;

        @ApiModelProperty(value = "这一行被券抵掉多少")
        private BigDecimal discountAmount;

        @ApiModelProperty(value = "这一行用的积分（请求体里给什么回什么，没用分就是 0）")
        private Integer usedPoints;

        @ApiModelProperty(value = "这一行的积分抵掉多少钱（服务端按汇率折，客户端传了不算）")
        private BigDecimal pointsAmount;

        @ApiModelProperty(value = "这一行的实付 = 小计 − 券优惠 − 积分抵扣（提交后落 shop_order_item，退款和出库结成本都按它）")
        private BigDecimal paidAmount;
    }
}
