package com.shop.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.Result;
import com.shop.dto.ai.ConversationDto;
import com.shop.dto.ai.ConversationQueryDto;
import com.shop.entity.Conversation;
import com.shop.service.ConversationService;
import com.shop.utils.PageUtil;
import com.shop.vo.ai.ConversationVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/conversation")
@Api(tags = "AI-会话管理")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/list")
    @ApiOperation(value = "会话列表")
    public Result<IPage<ConversationVo>> list(ConversationQueryDto dto) {
        Page<Object> page = PageUtil.getPage();
        return Result.success(conversationService.selectPage(page, dto));
    }

    @GetMapping("/detail/{id}")
    @ApiOperation(value = "会话详情")
    public Result<ConversationVo> detail(@PathVariable Long id) {
        return Result.success(conversationService.detail(id));
    }

    @PostMapping("/create")
    @ApiOperation(value = "新建会话")
    public Result<Conversation> create(@RequestBody ConversationDto dto) {
        return Result.success(conversationService.add(dto));
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改会话")
    public Result<Boolean> update(@RequestBody ConversationDto dto) {
        return Result.success(conversationService.update(dto));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除会话")
    public Result<Boolean> delete(@PathVariable List<Long> ids) {
        return Result.success(conversationService.delete(ids));
    }
}
