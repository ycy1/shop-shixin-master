package com.shop.controller.order;

import java.util.List;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import com.shop.entity.SysOrder;
import com.shop.service.SysOrderService;
import com.shop.common.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;

/**
 * 订单主表 控制器
 */
@RestController
@RequestMapping("/sys/sysOrder")
@RequiredArgsConstructor
@Api(tags = "订单主表管理")
public class SysOrderController {

    private final SysOrderService sysOrderService;

    @GetMapping("/list")
    @ApiOperation(value = "获取订单主表列表")
    public Result<IPage<SysOrder>> list(SysOrder sysOrder) {
        return Result.success(sysOrderService.selectPage(sysOrder));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取订单主表详情")
    public Result<SysOrder> getInfo(@PathVariable("id") Long id) {
        return Result.success(sysOrderService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加订单主表")
    public Result<Object> add(@RequestBody SysOrder sysOrder) {
        return Result.success(sysOrderService.insert(sysOrder));
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改订单主表")
    public Result<Object> edit(@RequestBody SysOrder sysOrder) {
        return Result.success(sysOrderService.update(sysOrder));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除订单主表")
    public Result<Object> remove(@PathVariable List<Long> ids) {
        return Result.success(sysOrderService.deleteByIds(ids));
    }
}
