package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopCategory;
import com.shop.service.ShopCategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品类目管理")
@RestController
@RequestMapping("/shop/category")
@RequiredArgsConstructor
public class ShopCategoryController {

    private final ShopCategoryService shopCategoryService;

    @GetMapping
    @ApiOperation(value = "获取类目树")
    public Result<List<ShopCategory>> listCategoryTree(ShopCategory category) {
        return Result.success(shopCategoryService.listCategoryTree(category));
    }

    @PostMapping
    @ApiOperation(value = "新增类目")
    @OperationLogger("新增商品类目")
    @SaCheckPermission("shop:category:add")
    public Result<Void> addCategory(@RequestBody ShopCategory category) {
        shopCategoryService.addCategory(category);
        return Result.success();
    }

    @PutMapping
    @ApiOperation(value = "修改类目")
    @OperationLogger("修改商品类目")
    @SaCheckPermission("shop:category:update")
    public Result<Void> updateCategory(@RequestBody ShopCategory category) {
        shopCategoryService.updateCategory(category);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除类目")
    @OperationLogger("删除商品类目")
    @SaCheckPermission("shop:category:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        shopCategoryService.deleteCategory(ids);
        return Result.success();
    }
}
