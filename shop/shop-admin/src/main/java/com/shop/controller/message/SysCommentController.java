package com.shop.controller.message;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.Result;
import com.shop.dto.message.SysCommentQueryDto;
import com.shop.service.SysCommentService;
import com.shop.vo.comment.SysCommentVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Api(tags = "评论管理")
@RequestMapping("/sys/comment")
@RequiredArgsConstructor
public class SysCommentController {

    private final SysCommentService sysCommentService;

    @GetMapping("/list")
    @ApiOperation(value = "获取评论列表")
    public Result<Page<SysCommentVO>> list(SysCommentQueryDto queryDto) {
        return Result.success(sysCommentService.selectList(queryDto));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除评论")
    @SaCheckPermission("sys:comment:delete")
    public Result<Void> delete(@PathVariable List<Integer> ids) {
        sysCommentService.removeBatchByIds(ids);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "编辑评论")
    @SaCheckPermission("sys:comment:update")
    public Result<Void> update(@RequestBody SysCommentVO commentVO) {
        sysCommentService.editComment(commentVO);
        return Result.success();
    }
}
