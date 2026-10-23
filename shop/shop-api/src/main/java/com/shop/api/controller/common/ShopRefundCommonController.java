package com.shop.api.controller.common;

import com.shop.api.service.ShopRefundCallbackService;
import com.shop.api.service.ShopRefundCreateService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.refund.ShopRefundCallbackDto;
import com.shop.dto.refund.ShopRefundCreateDto;
import com.shop.vo.refund.ShopRefundCallbackVo;
import com.shop.vo.refund.ShopRefundCreateVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopRefundCommonController
 * @date 2026/9/21 10:30
 */
@Api(tags = "shop 退款公共接口")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopRefundCommonController extends BaseCommController {

    private final ShopRefundCreateService shopRefundCreateService;

    private final ShopRefundCallbackService shopRefundCallbackService;

    /**
     * 统一退货：买家（或后台代客）只传"退哪几行、各退几件"，落一张待审核的退款单。
     * 只到"提交申请"这一步 —— sys_refund 落 0 待审核，订单状态、明细行的已退数量、库存一分不动，
     * 那些都在退款真到账那一刻才做（下面那条回调）。审核在后台「退款管理」里点。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，
     * 即 POST /api/comm/shop/refund/create。/api/** 不过 Sa-Token 拦截器，
     * 但退货人必须明确，所以由服务层自己取登录态，取不到就报"请先登录后再提交退货"；
     * 消费者账号只能退自己名下的单，其它账号算代客提退。
     * 金额、退款类型（全额/部分）、行快照一律服务端算，表单传了也不认。
     */
    @PostMapping("/refund/create")
    @ApiOperation(value = "统一退货（按行提退，只落待审核的退款单）")
    public Result<ShopRefundCreateVo> createRefund(@RequestBody ShopRefundCreateDto form) {
        return Result.success(shopRefundCreateService.create(form));
    }

    /**
     * 退款结果通知：把一张已审核通过的退款单翻成终态。
     * 成功（默认）→ 4 退款成功，回写各行 returned_quantity（退满的行 ship_status → 2 已退货，
     * 只退了一部分的行 → 4 部分退货）、
     * 整单退满才把订单推成 4 已退款，并按发货仓建 inbound_source=1 线上退货 的待入库单
     * （库存要等仓库点「入库」才真加）；success=false → 5 退款失败，只记原因，别的都不动。
     *
     * 路径：POST /api/comm/shop/refund/callback。和支付回调一样，/api/** 不过 Sa-Token 拦截器，
     * 也就是这个端点自己没有任何鉴权：谁来调都能把一张退款单记成"钱退了"、把货记成退回仓里。
     * 所以真接渠道那一路时必须先验签通过再来调这里，绝不能把 URL 直接暴露给前端或网关让外部打。
     * 服务层靠 sys_refund.status 的 3 → 4 条件更新兜重放：渠道重试通知、或者被重复调用，
     * 第二次的 WHERE 不成立，既不会把已退数量加两遍，也不会重复建入库单。
     */
    @PostMapping("/refund/callback")
    @ApiOperation(value = "退款结果通知（验签后调用：落终态 + 回写明细 + 建退货入库单）")
    public Result<ShopRefundCallbackVo> refundCallback(@RequestBody ShopRefundCallbackDto form) {
        return Result.success(shopRefundCallbackService.callback(form));
    }
}
