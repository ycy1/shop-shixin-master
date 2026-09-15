package com.shop.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.Result;
import com.shop.service.MemoryService;
import com.shop.utils.PageUtil;
import com.shop.vo.ai.MemoryVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/memory")
@Api(tags = "AI-消息记录管理")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @GetMapping("/list")
    @ApiOperation(value = "消息记录列表")
    public Result<IPage<MemoryVo>> list(@RequestParam(required = false) Long conversationId) {
        Page<Object> page = PageUtil.getPage();
        return Result.success(memoryService.selectPage(page, conversationId));
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation(value = "删除消息记录")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(memoryService.deleteMemory(id));
    }
}
