package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopIcon;
import com.shop.service.ShopIconService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "图标库管理")
@RestController
@RequestMapping("/shop/icon")
@RequiredArgsConstructor
public class ShopIconController {

    private final ShopIconService shopIconService;

    @GetMapping("/list")
    @ApiOperation(value = "获取图标列表")
    public Result<IPage<ShopIcon>> list(ShopIcon icon) {
        return Result.success(shopIconService.selectPage(icon));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的图标选项（供图标选择器使用，不分页）")
    public Result<List<ShopIcon>> options(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(shopIconService.listOptions(categoryId));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取图标详情")
    public Result<ShopIcon> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopIconService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加图标")
    @OperationLogger("添加图标")
    @SaCheckPermission("shop:icon:add")
    public Result<Long> add(@RequestBody ShopIcon icon) {
        shopIconService.insert(icon);
        // save() 会把自增主键回填到入参实体
        return Result.success(icon.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改图标")
    @OperationLogger("修改图标")
    @SaCheckPermission("shop:icon:update")
    public Result<Void> edit(@RequestBody ShopIcon icon) {
        shopIconService.update(icon);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除图标")
    @OperationLogger("删除图标")
    @SaCheckPermission("shop:icon:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopIconService.deleteByIds(ids);
        return Result.success();
    }
}
