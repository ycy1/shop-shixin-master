package com.shop.api.controller.common;

import com.shop.api.service.ShopDeliveryAcceptService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.delivery.ShopDeliveryAcceptDto;
import com.shop.dto.delivery.ShopDeliveryActionDto;
import com.shop.vo.delivery.ShopDeliveryAcceptVo;
import com.shop.vo.delivery.ShopDeliveryActionVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopRiderCommonController
 * @date 2026/9/19 22:25
 */
@Api(tags = "shop 公共接口（配送单接单 / 取货 / 送达 / 取消）")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopRiderCommonController extends BaseCommController {


    private final ShopDeliveryAcceptService shopDeliveryAcceptService;


    /**
     * 统一接单：把一张「待接单」的配送单揽到某个送货员名下，顺带按他的交通工具估出预计时长和预计送达，
     * 并把这一单里已经出满的那几行订单明细标成「已发货」。
     *
     * 路径：POST /api/comm/shop/delivery/accept。和下单、支付回调同一位置，理由是"两端同一个动作"：
     * 送货员端（他那份"派给自己的 + 还没人接的"列表）和后台「配送管理」列表上那颗「接单」按钮
     * 走的必须是这一条，不然两边各写一套、状态就会分叉。
     *
     * /api/** 不过 Sa-Token 拦截器，所以这一条也没有 @SaCheckPermission —— 资格判的是登录态加账号类型：
     * 送货员账号（account_type=2）只能把单接成自己的，其它账号属于"替某位送货员确认接单"、必须点名是哪一位。
     * 具体哪一单接得动、重复点击怎么算，全在 ShopDeliveryAcceptServiceImpl 的类注释里。
     */
    @PostMapping("/delivery/accept")
    @ApiOperation(value = "配送单接单（送货员本人接 or 后台指定送货员接：翻已接单 + 估预计时长 + 明细标已发货）")
    public Result<ShopDeliveryAcceptVo> acceptDelivery(@RequestBody ShopDeliveryAcceptDto form) {
        return Result.success(shopDeliveryAcceptService.accept(form));
    }


    /**
     * 取货：把一张「2 已接单」的配送单推到「3 配送中」，记 pickup_time（只翻配送单自己那一格）。
     *
     * 路径：POST /api/comm/shop/delivery/pickup。和接单同一个理由、同一个位置：送货员那一端点这一颗，
     * 和后台「配送管理」里替他记这一颗，必须是同一件事。
     * 入参只有"哪一单"，没有 riderId —— 人已经写在单子上了，谁能动这一单见 ShopDeliveryAcceptServiceImpl#assertRunningBy。
     */
    @PostMapping("/delivery/pickup")
    @ApiOperation(value = "配送单取货（已接单 → 配送中，记取货时间）")
    public Result<ShopDeliveryActionVo> pickupDelivery(@RequestBody ShopDeliveryActionDto form) {
        return Result.success(shopDeliveryAcceptService.pickup(form));
    }

    /**
     * 送达：把一张「3 配送中」的配送单推到「4 已送达」，记 delivered_time。
     *
     * 路径：POST /api/comm/shop/delivery/delivered。必须从 3 过来（没取货就点送达会被拒）。
     * 这一颗只写配送单，不回写 sys_order 的收货状态 —— 那一路是买家确认收货推的，两边别互相抢。
     */
    @PostMapping("/delivery/delivered")
    @ApiOperation(value = "配送单送达（配送中 → 已送达，记送达时间）")
    public Result<ShopDeliveryActionVo> deliveredDelivery(@RequestBody ShopDeliveryActionDto form) {
        return Result.success(shopDeliveryAcceptService.delivered(form));
    }

    /**
     * 取消：把一张还没送到的配送单（0/1/2/3 四档之一）置成「5 已取消」，记 cancel_time + cancel_reason，
     * 并把这一单名下「已发货」的订单明细行退回「已出库」。
     *
     * 路径：POST /api/comm/shop/delivery/cancel。和另外两路取消的分工：
     * 订单被取消/关闭带下来的那一路在 ShopOrderDeliveryServiceImpl#cancelByOrder（只收 0/1，顺带清派单痕迹）；
     * 这一路是人在按钮前点"这一单不送了"，2/3 那两条货已经在路上，所以 rider_id 和各步时间一律留着不改（要留痕）。
     * 已送达那一档不在这里退，退货走订单那边的退款接口。
     */
    @PostMapping("/delivery/cancel")
    @ApiOperation(value = "配送单取消（0/1/2/3 → 已取消，记取消原因 + 明细行退回已出库）")
    public Result<ShopDeliveryActionVo> cancelDelivery(@RequestBody ShopDeliveryActionDto form) {
        return Result.success(shopDeliveryAcceptService.cancel(form));
    }
}
