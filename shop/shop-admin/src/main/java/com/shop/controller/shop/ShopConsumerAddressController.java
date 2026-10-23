package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.address.ShopAddressQueryDto;
import com.shop.dto.address.ShopAddressSaveDto;
import com.shop.service.ShopConsumerAddressService;
import com.shop.vo.address.ShopAddressVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "收货地址管理")
@RestController
@RequestMapping("/shop/address")
@RequiredArgsConstructor
public class ShopConsumerAddressController {

    private final ShopConsumerAddressService shopConsumerAddressService;

    @GetMapping("/list")
    @ApiOperation(value = "获取收货地址列表")
    public Result<IPage<ShopAddressVo>> list(ShopAddressQueryDto query) {
        return Result.success(shopConsumerAddressService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取收货地址详情")
    public Result<ShopAddressVo> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopConsumerAddressService.selectAddressById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加收货地址")
    @OperationLogger("添加收货地址")
    @SaCheckPermission("shop:address:add")
    public Result<Void> add(@RequestBody ShopAddressSaveDto form) {
        shopConsumerAddressService.insert(form);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改收货地址")
    @OperationLogger("修改收货地址")
    @SaCheckPermission("shop:address:update")
    public Result<Void> edit(@RequestBody ShopAddressSaveDto form) {
        shopConsumerAddressService.update(form);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除收货地址")
    @OperationLogger("删除收货地址")
    @SaCheckPermission("shop:address:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopConsumerAddressService.deleteByIds(ids);
        return Result.success();
    }
}
