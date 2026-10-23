package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopBrand;
import com.shop.service.ShopBrandService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "品牌管理")
@RestController
@RequestMapping("/shop/brand")
@RequiredArgsConstructor
public class ShopBrandController {

    private final ShopBrandService shopBrandService;

    @GetMapping("/list")
    @ApiOperation(value = "获取品牌列表")
    public Result<IPage<ShopBrand>> list(ShopBrand brand) {
        return Result.success(shopBrandService.selectPage(brand));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的品牌选项（供商品表单下拉使用，不分页）")
    public Result<List<ShopBrand>> options(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(shopBrandService.listOptions(categoryId));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取品牌详情")
    public Result<ShopBrand> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopBrandService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加品牌")
    @OperationLogger("添加品牌")
    @SaCheckPermission("shop:brand:add")
    public Result<Long> add(@RequestBody ShopBrand brand) {
        shopBrandService.insert(brand);
        // save() 会把自增主键回填到入参实体
        return Result.success(brand.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改品牌")
    @OperationLogger("修改品牌")
    @SaCheckPermission("shop:brand:update")
    public Result<Void> edit(@RequestBody ShopBrand brand) {
        shopBrandService.update(brand);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除品牌")
    @OperationLogger("删除品牌")
    @SaCheckPermission("shop:brand:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopBrandService.deleteByIds(ids);
        return Result.success();
    }
}
