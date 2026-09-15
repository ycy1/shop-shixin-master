package com.shop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.JobLogQuery;
import com.shop.entity.SysJobLog;
import com.shop.service.SysJobLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "定时任务日志")
@RestController
@RequestMapping("/monitor/jobLog")
@RequiredArgsConstructor
public class SysJobLogController {

    private final SysJobLogService jobLogService;

    @ApiOperation(value = "获取定时任务日志列表")
    @GetMapping("/list")
    public Result<Page<SysJobLog>> list(JobLogQuery query) {
        return Result.success(jobLogService.selectJobLogPage(query));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除定时任务日志")
    @OperationLogger(value = "删除定时任务日志")
    @SaCheckPermission("sys:jobLog:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        jobLogService.removeBatchByIds(ids);
        return Result.success();
    }

    @DeleteMapping("/clean")
    @ApiOperation(value = "清空定时任务日志")
    @OperationLogger(value = "清空定时任务日志")
    @SaCheckPermission("sys:jobLog:clean")
    public Result<Void> clean() {
        jobLogService.cleanJobLog();
        return Result.success();
    }
}
