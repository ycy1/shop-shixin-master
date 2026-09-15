package com.shop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.Result;
import com.shop.dto.ai.AgentDto;
import com.shop.dto.ai.AgentQueryDto;
import com.shop.entity.Agent;
import com.shop.service.AgentService;
import com.shop.utils.PageUtil;
import com.shop.vo.ai.AgentVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sys/agent")
@Api(tags = "AI-智能体管理")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping("/list")
    @ApiOperation(value = "智能体列表")
    public Result<IPage<AgentVo>> list(AgentQueryDto dto) {
        Page<Agent> page = PageUtil.getPage();
        return Result.success(agentService.selectPage(page, dto));
    }

    @GetMapping("/detail/{id}")
    @ApiOperation(value = "智能体详情")
    public Result<AgentVo> detail(@PathVariable Long id) {
        return Result.success(agentService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "新增智能体")
    @SaCheckPermission("sys:agent:add")
    public Result<Boolean> add(@RequestBody AgentDto dto) {
        return Result.success(agentService.add(dto));
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改智能体")
    @SaCheckPermission("sys:agent:update")
    public Result<Boolean> update(@RequestBody AgentDto dto) {
        return Result.success(agentService.update(dto));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除智能体")
    @SaCheckPermission("sys:agent:delete")
    public Result<Boolean> delete(@PathVariable List<Long> ids) {
        return Result.success(agentService.delete(ids));
    }

    @PutMapping("/updateStatus")
    @ApiOperation(value = "修改智能体状态")
    @SaCheckPermission("sys:agent:updateStatus")
    public Result<Boolean> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        return Result.success(agentService.updateStatus(id, status));
    }

    @PutMapping("/refresh-knowledge/{id}")
    @ApiOperation(value = "刷新知识库内容")
    public Result<Boolean> refreshKnowledge(@PathVariable Long id) {
        return Result.success(agentService.refreshKnowledge(id));
    }
}
