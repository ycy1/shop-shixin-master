package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopParkUserTicket;
import com.shop.service.ShopParkUserTicketService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户票种（一张票发到一个人手上一行）
 *
 * 「订单管理 → 用户票种」和「消费者管理 → 我的订单」的票种 tab 读的都是这里的 list：
 * 消费者账号进来时服务层会把 userId 钉成本人，所以两端共用一条查询、口径不会走岔。
 * 这一期只有后台代客出票这一条写入路（还没接下单支付），所以增删改都开在后台。
 */
@Api(tags = "主题公园用户票种")
@RestController
@RequestMapping("/shop/userTicket")
@RequiredArgsConstructor
public class ShopParkUserTicketController {

    private final ShopParkUserTicketService shopParkUserTicketService;

    @GetMapping("/list")
    @ApiOperation(value = "获取用户票列表（keyword 搜票码/票种名/持票人姓名电话；parkId/ticketType/status/userId 精确匹配；beginVisitDate/endVisitDate 卡游玩日期）")
    public Result<IPage<ShopParkUserTicket>> list(ShopParkUserTicket userTicket) {
        return Result.success(shopParkUserTicketService.selectPage(userTicket));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取一张票的详情")
    public Result<ShopParkUserTicket> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopParkUserTicketService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "代客出票（票码由服务端生成；票面那几格和园区按票种行带成快照，传了也不认）")
    @OperationLogger("主题公园代客出票")
    @SaCheckPermission("shop:userTicket:add")
    public Result<Long> add(@RequestBody ShopParkUserTicket userTicket) {
        shopParkUserTicketService.insert(userTicket);
        return Result.success(userTicket.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改一张票（只放开游玩日期、时段、持票人三格和备注，且只有还没用过的票改得动）")
    @OperationLogger("修改用户票种")
    @SaCheckPermission("shop:userTicket:update")
    public Result<Void> edit(@RequestBody ShopParkUserTicket userTicket) {
        shopParkUserTicketService.update(userTicket);
        return Result.success();
    }

    @PutMapping("/verify/{id}")
    @ApiOperation(value = "入园核销：0 未使用 → 1 已核销，同时盖核销时间")
    @OperationLogger("用户票种核销")
    @SaCheckPermission("shop:userTicket:verify")
    public Result<Void> verify(@PathVariable("id") Long id) {
        shopParkUserTicketService.verify(id);
        return Result.success();
    }

    @PutMapping("/refund/{id}")
    @ApiOperation(value = "退款：0 未使用 / 1 已核销 → 3 已退款，同时盖退款时间（只改状态，这一期没有支付链路可退钱）")
    @OperationLogger("用户票种退款")
    @SaCheckPermission("shop:userTicket:refund")
    public Result<Void> refund(@PathVariable("id") Long id) {
        shopParkUserTicketService.refund(id);
        return Result.success();
    }

    /**
     * 重出票面二维码。
     *
     * 权限走的是这一行已有的 `shop:userTicket:update`（同一张票、同样是改这一行），没另开一档 BUTTON，
     * 所以不用为它补菜单 SQL；要单独控这一颗的话再说。
     */
    @PutMapping("/qr/{id}")
    @ApiOperation(value = "重出这张票的票面二维码（票码不变，只重新生成并覆盖 qr_img）")
    @OperationLogger("用户票种重出二维码")
    @SaCheckPermission("shop:userTicket:update")
    public Result<String> refreshQr(@PathVariable("id") Long id) {
        return Result.success(shopParkUserTicketService.refreshQrCode(id));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除用户票（逻辑删除）")
    @OperationLogger("删除用户票种")
    @SaCheckPermission("shop:userTicket:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopParkUserTicketService.deleteByIds(ids);
        return Result.success();
    }
}
