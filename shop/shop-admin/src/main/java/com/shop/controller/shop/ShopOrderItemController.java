package com.shop.controller.shop;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.common.Result;
import com.shop.dto.order.ShopOrderQueryDto;
import com.shop.service.ShopOrderItemService;
import com.shop.vo.order.ShopOrderVo;
import com.shop.vo.refund.ShopOrderRefundVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单明细（后台「消费者管理 → 我的订单」）
 *
 * 这一页列表一行是一张订单（sys_order），详情弹窗上半是主单、下半是名下全部商品明细行；
 * 列表上那颗「退款单」看的是这一单名下的退货记录（一张订单可以退好几次，所以那一路是列表）。
 * 早期这里还有一对明细级的 /list + /{id}（一行一个商品），页面改到主表口径后一起删了。
 *
 * 只有读接口：下单在小程序侧，后台这一页不提供新增/修改/删除，所以也不挂 @SaCheckPermission
 * （项目约定读接口不做服务端权限校验，权限只到前端的详情按钮）。
 * 消费者账号能看到什么由 ShopOrderItemServiceImpl 按 user_id 收窄。
 */
@Api(tags = "我的订单管理")
@RestController
@RequestMapping("/shop/orderItem")
@RequiredArgsConstructor
public class ShopOrderItemController {

    private final ShopOrderItemService shopOrderItemService;

    @GetMapping("/order/list")
    @ApiOperation(value = "获取订单列表（一行一张订单，不含明细行）")
    public Result<IPage<ShopOrderVo>> orderList(ShopOrderQueryDto query) {
        return Result.success(shopOrderItemService.selectOrderPage(query));
    }

    @GetMapping("/order/{id}")
    @ApiOperation(value = "获取订单详情（主表字段 + 名下全部商品明细行）")
    public Result<ShopOrderVo> getOrderInfo(@PathVariable("id") Long id) {
        return Result.success(shopOrderItemService.selectOrderById(id));
    }

    @GetMapping("/order/refund/{orderId}")
    @ApiOperation(value = "获取一张订单名下的全部退款单（一行一张退款单，含它退了哪些商品行）")
    public Result<List<ShopOrderRefundVo>> orderRefundList(@PathVariable("orderId") Long orderId) {
        return Result.success(shopOrderItemService.selectRefundsByOrderId(orderId));
    }
}
