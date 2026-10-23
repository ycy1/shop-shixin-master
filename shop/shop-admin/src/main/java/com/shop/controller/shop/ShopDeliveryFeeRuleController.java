package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopDeliveryFeeRule;
import com.shop.service.ShopDeliveryFeeRuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "配送费规则管理")
@RestController
@RequestMapping("/shop/deliveryFeeRule")
@RequiredArgsConstructor
public class ShopDeliveryFeeRuleController {

    private final ShopDeliveryFeeRuleService shopDeliveryFeeRuleService;

    @GetMapping("/list")
    @ApiOperation(value = "获取配送费规则列表")
    public Result<IPage<ShopDeliveryFeeRule>> list(ShopDeliveryFeeRule query) {
        return Result.success(shopDeliveryFeeRuleService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取配送费规则详情")
    public Result<ShopDeliveryFeeRule> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopDeliveryFeeRuleService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加配送费规则")
    @OperationLogger("添加配送费规则")
    @SaCheckPermission("shop:deliveryFeeRule:add")
    public Result<Long> add(@RequestBody ShopDeliveryFeeRule rule) {
        shopDeliveryFeeRuleService.insert(rule);
        // save() 会把自增主键回填到入参实体
        return Result.success(rule.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改配送费规则")
    @OperationLogger("修改配送费规则")
    @SaCheckPermission("shop:deliveryFeeRule:update")
    public Result<Void> edit(@RequestBody ShopDeliveryFeeRule rule) {
        shopDeliveryFeeRuleService.update(rule);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除配送费规则")
    @OperationLogger("删除配送费规则")
    @SaCheckPermission("shop:deliveryFeeRule:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopDeliveryFeeRuleService.deleteByIds(ids);
        return Result.success();
    }
}
