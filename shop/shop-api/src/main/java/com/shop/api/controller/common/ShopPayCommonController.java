package com.shop.api.controller.common;

import com.shop.api.service.ShopOrderCancelService;
import com.shop.api.service.ShopOrderCreateService;
import com.shop.api.service.ShopOrderPaidService;
import com.shop.api.service.ShopOrderReceiveService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.order.ShopOrderCreateDto;
import com.shop.dto.order.ShopOrderPaidDto;
import com.shop.vo.order.ShopOrderCancelVo;
import com.shop.vo.order.ShopOrderCreateVo;
import com.shop.vo.order.ShopOrderPaidVo;
import com.shop.vo.order.ShopOrderPreviewVo;
import com.shop.vo.order.ShopOrderReceiveVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopPayCommonController
 * @date 2026/9/19 22:25
 */
@Api(tags = "shop 公共接口（下单试算 / 统一下单 / 取消订单 / 支付回调 / 确认收货）")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopPayCommonController extends BaseCommController {

    private final ShopOrderCreateService shopOrderCreateService;

    private final ShopOrderPaidService shopOrderPaidService;

    private final ShopOrderReceiveService shopOrderReceiveService;

    private final ShopOrderCancelService shopOrderCancelService;


    /**
     * 统一下单：客户端只传买了哪些商品，订单主表 + 明细一次落库。
     * 只到"下单"这一步——主单落 0 待支付，不动支付也不动库存，后续支付接口按返回的 orderNo 接着做。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，
     * 即 POST /api/comm/shop/order/create。/api/** 不过 Sa-Token 拦截器，
     * 但下单人必须明确，所以由服务层自己取登录态，取不到就报"请先登录后再下单"。
     */
    @PostMapping("/order/create")
    @ApiOperation(value = "统一下单（下单前调用，只落单不支付）")
    public Result<ShopOrderCreateVo> createOrder(@RequestBody ShopOrderCreateDto form) {
        return Result.success(shopOrderCreateService.create(form));
    }

    /**
     * 下单试算：结算页在买家点提交之前把这一单的钱问一遍——商品合计、运费、每张优惠券各抵了多少、
     * 优惠合计、应付总额、预计实付，外加逐行商品的优惠。
     *
     * 路径：POST /api/comm/shop/order/preview。**入参和上面统一下单完全一样**（同一个人、同一个收货地址、
     * 同一批明细行、同一串 couponIds），因为算钱的这一段两边共用：这里报多少，提交就收多少。
     * 和下单的区别只在它不写库——不落订单、不锁库存、也不核销券，纯粹是问一遍。
     *
     * 下单会被拒的那几种情况（库存不够、券不是这个人的/用过/过期/够不上门槛/范围内没货），
     * 这里一样按同样的话抛回去，让买家在结算页就看见，而不是点了提交才知道。
     * 同样要登录：券的归属按下单人判，没登录态连"这几张券能不能用"都算不了。
     */
    @PostMapping("/order/preview")
    @ApiOperation(value = "下单试算（不落库：算出各项金额、逐张券抵扣和运费）")
    public Result<ShopOrderPreviewVo> previewOrder(@RequestBody ShopOrderCreateDto form) {
        return Result.success(shopOrderCreateService.preview(form));
    }

    /**
     * 取消订单：买家在付款之前反悔了，把一张「0 待支付」的订单作废掉，顺手把它占用的那几样东西原路还回去。
     *
     * 路径：POST /api/comm/shop/order/cancel/{id}（前端调 comm 要写双前缀）。一次取消做完六件事，
     * 在同一个事务里：sys_order.status 0 → 3 已取消、这一单名下那条下单时就建好的配送单 → 5 已取消、
     * 各行锁在发货仓上的那份可用量还回库存、这一单核销掉的优惠券退回买家手上、冻住的积分和占住的余额
     * 各自整笔还回去（细则见 ShopOrderCancelService）。
     *
     * **只认还没付钱的那一档**：已经付过的单要退的是钱，得走统一退货那条链（建退款单 → 退款回调），
     * 从这儿取消会把一张有交易的单悄悄标成"没成交"。
     *
     * /api/** 不过 Sa-Token 拦截器，所以服务层自己取登录态：消费者账号只能取消自己名下的单，
     * 后台各账号点这一颗算代客取消（口径同上面确认收货那一条）。
     */
    @PostMapping("/order/cancel/{id}")
    @ApiOperation(value = "取消订单（未支付：订单和配送单置取消 + 释放库存 + 退回优惠券）")
    public Result<ShopOrderCancelVo> cancelOrder(@PathVariable("id") Long id) {
        return Result.success(shopOrderCancelService.cancel(id));
    }

    /**
     * 支付结果通知：主单 0 待支付 → 1 已支付（补 pay_time），并按订单明细生成一张 outbound_source=1 的商品出库单，
     * 出库单的成本价和总金额同时按订单明细的实付金额结转（之后后台出库时改不了）。
     * 只记账不动库存 —— 下单时锁住的那份可用量要等到后台点「出库」才真减（见 ShopOrderPaidServiceImpl 类注释）。
     *
     * 路径：POST /api/comm/shop/order/payCallback。和上面下单接口一样，/api/** 不过 Sa-Token 拦截器，
     * 也就是这个端点自己没有任何鉴权：谁来调都能把一张订单翻成"已支付"。
     * 所以微信那一路必须在 WxPayServiceImpl#jsapiNotify 里验签（WechatPay2ValidatorForRequest）通过之后
     * 再来调这里，绝不能把 URL 直接暴露给前端或网关让外部打。
     * 服务层靠 sys_order.status 的 0 → 1 条件更新兜重放：微信重试通知、或者被重复调用，
     * 第二次的 WHERE status = 0 不成立，不会重复改状态也不会重复建出库单。
     */
    @PostMapping("/order/payCallback")
    @ApiOperation(value = "支付结果通知（验签后调用：落已支付 + 生成出库单）")
    public Result<ShopOrderPaidVo> payCallback(@RequestBody ShopOrderPaidDto form) {
        return Result.success(shopOrderPaidService.paid(form));
    }

    /**
     * 确认收货：买家收到货了，把一张「1 已支付」的订单推成「2 已完成」。
     *
     * 路径：POST /api/comm/shop/order/receive/{id}（前端调 comm 要写双前缀）。判据只有一条：
     * 这一单名下**每一行商品的货都已有结论** —— 1 已发货（那一档由统一接单写），或退了货的
     * 4 部分退货 / 2 已退货（放行，否则带退货行的单永远收不了尾）；细则见 ShopOrderReceiveService#receive。
     *
     * /api/** 不过 Sa-Token 拦截器，所以服务层自己取登录态：消费者账号只能确认自己名下的单，
     * 后台各账号点这一颗算代客确认（口径同统一退货那一条）。
     * 这一颗只写 sys_order.status，不回动明细行、库存和那张配送单。
     */
    @PostMapping("/order/receive/{id}")
    @ApiOperation(value = "确认收货（名下商品行已全部发货/已退货时，订单推成已完成）")
    public Result<ShopOrderReceiveVo> receiveOrder(@PathVariable("id") Long id) {
        return Result.success(shopOrderReceiveService.receive(id));
    }

}
