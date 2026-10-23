package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopProductSpec;
import com.shop.service.ShopProductSpecService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品规格管理")
@RestController
@RequestMapping("/shop/spec")
@RequiredArgsConstructor
public class ShopProductSpecController {

    private final ShopProductSpecService shopProductSpecService;

    @GetMapping("/list")
    @ApiOperation(value = "获取商品规格列表")
    public Result<IPage<ShopProductSpec>> list(ShopProductSpec spec) {
        return Result.success(shopProductSpecService.selectPage(spec));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的规格选项（供商品表单下拉使用，不分页）")
    public Result<List<ShopProductSpec>> options(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(shopProductSpecService.listOptions(categoryId));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取商品规格详情")
    public Result<ShopProductSpec> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopProductSpecService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加商品规格")
    @OperationLogger("添加商品规格")
    @SaCheckPermission("shop:spec:add")
    public Result<Long> add(@RequestBody ShopProductSpec spec) {
        shopProductSpecService.insert(spec);
        // save() 会把自增主键回填到入参实体，返回给前端用于新建后直接勾选
        return Result.success(spec.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改商品规格")
    @OperationLogger("修改商品规格")
    @SaCheckPermission("shop:spec:update")
    public Result<Void> edit(@RequestBody ShopProductSpec spec) {
        shopProductSpecService.update(spec);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除商品规格")
    @OperationLogger("删除商品规格")
    @SaCheckPermission("shop:spec:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopProductSpecService.deleteByIds(ids);
        return Result.success();
    }
}
