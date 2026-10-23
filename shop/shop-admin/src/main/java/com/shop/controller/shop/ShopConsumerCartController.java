package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.cart.ShopCartQueryDto;
import com.shop.dto.cart.ShopCartSaveDto;
import com.shop.service.ShopConsumerCartService;
import com.shop.vo.cart.ShopCartVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "购物车管理")
@RestController
@RequestMapping("/shop/cart")
@RequiredArgsConstructor
public class ShopConsumerCartController {

    private final ShopConsumerCartService shopConsumerCartService;

    @GetMapping("/list")
    @ApiOperation(value = "获取购物车列表")
    public Result<IPage<ShopCartVo>> list(ShopCartQueryDto query) {
        return Result.success(shopConsumerCartService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取购物车记录详情")
    public Result<ShopCartVo> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopConsumerCartService.selectCartById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加购物车记录")
    @OperationLogger("添加购物车记录")
    @SaCheckPermission("shop:cart:add")
    public Result<Void> add(@RequestBody ShopCartSaveDto form) {
        shopConsumerCartService.insert(form);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改购物车记录")
    @OperationLogger("修改购物车记录")
    @SaCheckPermission("shop:cart:update")
    public Result<Void> edit(@RequestBody ShopCartSaveDto form) {
        shopConsumerCartService.update(form);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除购物车记录")
    @OperationLogger("删除购物车记录")
    @SaCheckPermission("shop:cart:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopConsumerCartService.deleteByIds(ids);
        return Result.success();
    }
}
