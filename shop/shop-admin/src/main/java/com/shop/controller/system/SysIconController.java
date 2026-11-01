package com.shop.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.SysIcon;
import com.shop.service.SysIconService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "图标库管理")
@RestController
@RequestMapping("/sys/icon")
@RequiredArgsConstructor
public class SysIconController {

    private final SysIconService sysIconService;

    @GetMapping("/list")
    @ApiOperation(value = "获取图标列表")
    public Result<IPage<SysIcon>> list(SysIcon icon) {
        return Result.success(sysIconService.selectPage(icon));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的图标选项（供图标选择器使用，不分页）")
    public Result<List<SysIcon>> options(@RequestParam(value = "iconCategory", required = false) Integer iconCategory,
                                        @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(sysIconService.listOptions(iconCategory, categoryId));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取图标详情")
    public Result<SysIcon> getInfo(@PathVariable("id") Long id) {
        return Result.success(sysIconService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加图标")
    @OperationLogger("添加图标")
    @SaCheckPermission("sys:icon:add")
    public Result<Long> add(@RequestBody SysIcon icon) {
        sysIconService.insert(icon);
        // save() 会把自增主键回填到入参实体
        return Result.success(icon.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改图标")
    @OperationLogger("修改图标")
    @SaCheckPermission("sys:icon:update")
    public Result<Void> edit(@RequestBody SysIcon icon) {
        sysIconService.update(icon);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除图标")
    @OperationLogger("删除图标")
    @SaCheckPermission("sys:icon:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        sysIconService.deleteByIds(ids);
        return Result.success();
    }
}
