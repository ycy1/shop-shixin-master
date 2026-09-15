package com.shop.controller.order;

import java.util.List;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import com.shop.annotation.OperationLogger;
import com.shop.entity.SysRefund;
import com.shop.service.SysRefundService;
import com.shop.common.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;

/**
 * 退款表 控制器
 */
@RestController
@RequestMapping("/sys/sysRefund")
@RequiredArgsConstructor
@Api(tags = "退款表管理")
public class SysRefundController {

    private final SysRefundService sysRefundService;

    @GetMapping("/list")
    @ApiOperation(value = "获取退款表列表")
    public Result<IPage<SysRefund>> list(SysRefund sysRefund) {
        return Result.success(sysRefundService.selectPage(sysRefund));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取退款表详情")
    public Result<SysRefund> getInfo(@PathVariable("id") Long id) {
        return Result.success(sysRefundService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加退款表")
    public Result<Object> add(@RequestBody SysRefund sysRefund) {
        return Result.success(sysRefundService.insert(sysRefund));
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改退款表")
    public Result<Object> edit(@RequestBody SysRefund sysRefund) {
        return Result.success(sysRefundService.update(sysRefund));
    }

    @PutMapping("/audit")
    @ApiOperation(value = "审核退款")
    @OperationLogger(value = "审核退款")
    public Result<Object> audit(@RequestBody SysRefund sysRefund) {
        return Result.success(sysRefundService.audit(sysRefund));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除退款表")
    public Result<Object> remove(@PathVariable List<Long> ids) {
        return Result.success(sysRefundService.deleteByIds(ids));
    }
}
