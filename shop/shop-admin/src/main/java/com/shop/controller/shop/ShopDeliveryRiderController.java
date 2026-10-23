package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopDeliveryRider;
import com.shop.service.ShopDeliveryRiderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "送货员管理")
@RestController
@RequestMapping("/shop/rider")
@RequiredArgsConstructor
public class ShopDeliveryRiderController {

    private final ShopDeliveryRiderService shopDeliveryRiderService;

    @GetMapping("/list")
    @ApiOperation(value = "获取送货员列表")
    public Result<IPage<ShopDeliveryRider>> list(ShopDeliveryRider rider) {
        return Result.success(shopDeliveryRiderService.selectPage(rider));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的送货员选项（供配送单表单下拉使用，不分页）")
    public Result<List<ShopDeliveryRider>> options() {
        return Result.success(shopDeliveryRiderService.listOptions());
    }

    @GetMapping("/my")
    @ApiOperation(value = "获取当前登录账号自己的送货员档案（未建档时为空）")
    public Result<ShopDeliveryRider> my() {
        return Result.success(shopDeliveryRiderService.getMyInfo());
    }

    @PutMapping("/my")
    @ApiOperation(value = "修改自己的送货员信息")
    // 不能加 @OperationLogger：切面里写死了非 admin 角色一律抛 NotPermissionException，
    // 而这个接口正是给非 admin 的送货员本人用的。只改自己那行，不进操作日志。
    public Result<Void> editMy(@RequestBody ShopDeliveryRider rider) {
        shopDeliveryRiderService.updateMyInfo(rider);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取送货员详情")
    public Result<ShopDeliveryRider> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopDeliveryRiderService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加送货员")
    @OperationLogger("添加送货员")
    @SaCheckPermission("shop:rider:add")
    public Result<Long> add(@RequestBody ShopDeliveryRider rider) {
        shopDeliveryRiderService.insert(rider);
        // save() 会把自增主键回填到入参实体
        return Result.success(rider.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改送货员")
    @OperationLogger("修改送货员")
    @SaCheckPermission("shop:rider:update")
    public Result<Void> edit(@RequestBody ShopDeliveryRider rider) {
        shopDeliveryRiderService.update(rider);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除送货员")
    @OperationLogger("删除送货员")
    @SaCheckPermission("shop:rider:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopDeliveryRiderService.deleteByIds(ids);
        return Result.success();
    }
}
