package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopTrendyParkTicket;
import com.shop.service.ShopTrendyParkTicketService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "主题公园票种管理")
@RestController
@RequestMapping("/shop/parkTicket")
@RequiredArgsConstructor
public class ShopTrendyParkTicketController {

    private final ShopTrendyParkTicketService shopTrendyParkTicketService;

    @GetMapping("/list")
    @ApiOperation(value = "获取票种列表")
    public Result<IPage<ShopTrendyParkTicket>> list(ShopTrendyParkTicket ticket) {
        return Result.success(shopTrendyParkTicketService.selectPage(ticket));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取上架中的票种选项（供「用户票种」出票表单那格下拉使用，不分页；parkId 传了就只给这一家园区的）")
    public Result<List<ShopTrendyParkTicket>> options(@RequestParam(required = false) Long parkId) {
        return Result.success(shopTrendyParkTicketService.listOptions(parkId));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取票种详情")
    public Result<ShopTrendyParkTicket> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopTrendyParkTicketService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加票种（编号由服务端生成）")
    @OperationLogger("添加主题公园票种")
    @SaCheckPermission("shop:parkTicket:add")
    public Result<Long> add(@RequestBody ShopTrendyParkTicket ticket) {
        shopTrendyParkTicketService.insert(ticket);
        return Result.success(ticket.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改票种")
    @OperationLogger("修改主题公园票种")
    @SaCheckPermission("shop:parkTicket:update")
    public Result<Void> edit(@RequestBody ShopTrendyParkTicket ticket) {
        shopTrendyParkTicketService.update(ticket);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除票种（已经出出去过票的拦住）")
    @OperationLogger("删除主题公园票种")
    @SaCheckPermission("shop:parkTicket:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopTrendyParkTicketService.deleteByIds(ids);
        return Result.success();
    }
}
