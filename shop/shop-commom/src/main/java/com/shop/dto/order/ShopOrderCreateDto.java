package com.shop.dto.order;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 统一下单参数（小程序侧下单前调用，只落单、不支付）
 *
 * 只收"用户买了哪几个规格、各买几件、用哪几张券、每行花几分"：一份 Item 就是一个 shop_product_sku.id 加一个数量。
 * 单价、规格文本、商品名、主图、门店、各项金额都由服务端按这条 SKU 和它所属的商品算，客户端传了也不认；
 * **优惠金额也不再是入参**（2026-09-29 这一轮）：客户端只给 couponIds，
 * 每一张券能抵多少、抵在哪几行上，全由服务端按券的面额 + 适用范围 + 使用门槛现算
 *（判据与摊法见 ShopOrderCouponService），因为"这一行优惠多少"是要进账的钱，
 * 让前端报个数字进来就等于让它自己定价。
 * 积分这一路同理：客户端只给"这一行要花掉几分"（Item.usedPoints），
 * 这些分抵多少钱由服务端按 sys_config 里那一档汇率（points_exchange，1 元值多少分）现折
 *（判据与四拍走法见 ShopOrderPointsService），折出来的钱和用掉的分数都会落到明细行上。
 * 整单那一份（payPoints）走的是同一个格子：买家只报一个总数、由服务端按各行还剩多少钱可抵摊到行上，
 * 所以库里没有"整单积分"这一列，摊完就和行级那份合成同一个 used_points。
 * 支付方式（payType）是 2026-10-07 这一轮才加进来的：余额支付要在下单那一刻就把钱占住，
 * 所以这一格必须跟着请求体进来（不传就没人知道这一单要不要动钱包）。
 * 它落到 sys_order.pay_type 上，是后面三拍（支付结转 / 取消解冻 / 退款退回）认这条链唯一的记号，
 * 要占多少钱不再另存一列 —— 余额付的就是这一单的实付，也就是算完之后 sys_order.pay_amount 那一个数。
 * 下单人（sys_order.user_id 和明细的 user_id）参数里给就用参数、不给回落到当前登录用户。
 *
 * 仓库不用传：服务端按用户选用的收货地址（addressId）的经纬度就近挑一家有可用库存的仓，
 * 顺手把这家仓的库存锁定，明细落 warehouse_id（见 ShopOrderCreateServiceImpl#create）。
 * 没传 addressId 时回落到 sys_user 上的经纬度。
 *
 * 这一份入参下单试算（ShopOrderCreateService#preview，POST /api/comm/shop/order/preview）也照收不误：
 * 两个接口吃同一个请求体，试算那一趟不锁库存、不落库、不核销券，只把算出来的钱报回去。
 */
@Data
@ApiModel(value = "统一下单参数")
public class ShopOrderCreateDto {

    @ApiModelProperty(value = "下单用户ID(sys_user.id)，不传按当前登录用户")
    private Long userId;

    @ApiModelProperty(value = "用户选用的收货地址ID(shop_consumer_address.id)，必须是下单用户名下的地址；"
            + "就近分仓按这家地址的经纬度算，不传则用 sys_user 上的经纬度。"
            + "传了会原样落到 sys_order.address_id，支付回调分仓和后面的配送都读这一列")
    private Long addressId;

    @ApiModelProperty(value = "订单类型：1普通订单 2秒杀 3拼团 4充值（字典 order_type），不传按 1")
    private Integer orderType;

    @ApiModelProperty(value = "订单备注（买家留言）")
    private String remark;

    @ApiModelProperty(value = "要用的优惠券（用户券ID集合，取 shop_activity_coupon_user.id —— 即发到这个下单人名下的那一张，"
            + "不是模板 shop_activity_coupon.id）。不用券就不传。"
            + "这一串只表明「用哪几张」，每张抵多少钱由服务端按券面额、适用范围、使用门槛算并摊到明细行上；"
            + "券不是这个人的、已经用过/作废/过期的、这一单里没有它能抵的商品、够不上满减门槛，都当场拒单")
    private List<Long> couponIds;

    @ApiModelProperty(value = "这一单整体要用掉的积分总数（在各行自己那份 usedPoints 之外再抵的），不用就不传或传 0。"
            + "和逐行那份是同一个格子：服务端按各行「小计 − 券优惠 − 行级分已抵的钱」还剩多少的比例摊到行上、"
            + "凑不满一分的尾数从末行往前补满，摊完写进 shop_order_item.used_points，库里不另开一整单级的列。"
            + "上限是各行承接得住的分数之和（一行最多把自己抵到 0），超过就当场拒单；"
            + "摊完的总数仍要和账户上的可用积分一起过 ShopOrderPointsService#apply 那道余额闸")
    private Integer payPoints;

    @ApiModelProperty(value = "支付方式（字典 pay_type）：1微信 2支付宝 3银行卡 4余额，不传就是还没定。"
            + "只有 4 会在下单这一刻动钱包：这一单的实付全额从可用余额冻进冻结余额（判据与四拍见 ShopOrderWalletService），"
            + "其余三档只是把选中的渠道落到主单上，支付回调拿实际渠道覆盖它")
    private Integer payType;

    @ApiModelProperty(value = "购买明细，至少一条")
    private List<Item> items;

    /**
     * 一行 = 一个具体规格（SKU）；同一商品选两个规格就是两行，所以这里不按 skuId 去重
     */
    @Data
    @ApiModel(value = "统一下单明细行")
    public static class Item {

        @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)：下单选的就是某一个具体规格，"
                + "商品名/主图/门店在它所属的那件商品上")
        private Long skuId;

        @ApiModelProperty(value = "购买数量，大于 0")
        private Integer quantity;

        @ApiModelProperty(value = "这一行用掉多少积分（shop_activity_points 上这个下单人的可用积分），不用分就不传或传 0。"
                + "只有\"这一行要花掉几分\"是买家自己选的，能抵多少钱一律由服务端按 sys_config 的 points_exchange 折算："
                + "折出来的钱超过这一行优惠之后剩下的钱、或者各行加起来超过账户上的可用积分，都当场拒单。"
                + "积分在下单那一刻先冻在账户的 frozen_points 上，支付成功才真扣走（没付就取消会整笔解冻）")
        private Integer usedPoints;
    }
}
