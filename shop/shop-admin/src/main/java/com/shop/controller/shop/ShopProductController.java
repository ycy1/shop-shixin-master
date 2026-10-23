package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.product.ShopProductQueryDto;
import com.shop.dto.product.ShopProductSaveDto;
import com.shop.service.ShopProductService;
import com.shop.vo.product.ShopProductVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品管理")
@RestController
@RequestMapping("/shop/product")
@RequiredArgsConstructor
public class ShopProductController {

    private final ShopProductService shopProductService;

    @GetMapping("/list")
    @ApiOperation(value = "获取商品列表")
    public Result<IPage<ShopProductVo>> list(ShopProductQueryDto query) {
        return Result.success(shopProductService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取商品详情")
    public Result<ShopProductVo> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopProductService.selectProductById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加商品")
    @OperationLogger("添加商品")
    @SaCheckPermission("shop:product:add")
    public Result<Void> add(@RequestBody ShopProductSaveDto form) {
        shopProductService.insert(form);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改商品")
    @OperationLogger("修改商品")
    @SaCheckPermission("shop:product:update")
    public Result<Void> edit(@RequestBody ShopProductSaveDto form) {
        shopProductService.update(form);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除商品")
    @OperationLogger("删除商品")
    @SaCheckPermission("shop:product:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopProductService.deleteByIds(ids);
        return Result.success();
    }
}
