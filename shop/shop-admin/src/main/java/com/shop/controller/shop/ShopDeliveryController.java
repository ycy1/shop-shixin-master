package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopDelivery;
import com.shop.service.ShopDeliveryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "配送管理")
@RestController
@RequestMapping("/shop/delivery")
@RequiredArgsConstructor
public class ShopDeliveryController {

    private final ShopDeliveryService shopDeliveryService;

    @GetMapping("/list")
    @ApiOperation(value = "获取配送单列表")
    public Result<IPage<ShopDelivery>> list(ShopDelivery delivery) {
        return Result.success(shopDeliveryService.selectPage(delivery));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取配送单详情")
    public Result<ShopDelivery> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopDeliveryService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加配送单")
    @OperationLogger("添加配送单")
    @SaCheckPermission("shop:delivery:add")
    public Result<Long> add(@RequestBody ShopDelivery delivery) {
        shopDeliveryService.insert(delivery);
        // save() 会把自增主键回填到入参实体
        return Result.success(delivery.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改配送单")
    @OperationLogger("修改配送单")
    @SaCheckPermission("shop:delivery:update")
    public Result<Void> edit(@RequestBody ShopDelivery delivery) {
        shopDeliveryService.update(delivery);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除配送单")
    @OperationLogger("删除配送单")
    @SaCheckPermission("shop:delivery:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopDeliveryService.deleteByIds(ids);
        return Result.success();
    }
}
