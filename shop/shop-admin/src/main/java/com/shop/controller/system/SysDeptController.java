package com.shop.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.user.AddDeptUserDTO;
import com.shop.entity.SysDept;
import com.shop.service.SysDeptService;
import com.shop.vo.user.SysUserVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "部门管理")
@RestController
@RequestMapping("/sys/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;

    @GetMapping
    @ApiOperation(value = "获取部门树")
    public Result<List<SysDept>> listDeptTree(SysDept dept) {
        return Result.success(sysDeptService.listDeptTree(dept));
    }

    @PostMapping
    @ApiOperation(value = "新增部门")
    @OperationLogger("新增部门")
    @SaCheckPermission("sys:dept:add")
    public Result<Void> addDept(@RequestBody SysDept dept) {
        sysDeptService.addDept(dept);
        return Result.success();
    }

    @PutMapping
    @ApiOperation(value = "修改部门")
    @OperationLogger("修改部门")
    @SaCheckPermission("sys:dept:update")
    public Result<Void> updateDept(@RequestBody SysDept dept) {
        sysDeptService.updateDept(dept);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除部门")
    @OperationLogger("删除部门")
    @SaCheckPermission("sys:dept:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysDeptService.deleteDept(ids);
        return Result.success();
    }

    @GetMapping("/users/{deptId}")
    @SaCheckPermission("sys:dept:user:list")
    @ApiOperation(value = "获取部门下的人员列表")
    public Result<IPage<SysUserVo>> getDeptUserPage(@PathVariable Long deptId) {
        return Result.success(sysDeptService.getDeptUserPage(deptId));
    }

    @PostMapping("/users")
    @ApiOperation(value = "添加部门人员")
    @OperationLogger("添加部门人员")
    @SaCheckPermission("sys:dept:user:add")
    public Result<Void> addDeptUsers(@RequestBody AddDeptUserDTO dto) {
        sysDeptService.addDeptUsers(dto.getDeptId(), dto.getUserIds());
        return Result.success();
    }
}
