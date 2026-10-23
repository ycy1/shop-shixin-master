package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopStore;
import com.shop.service.ShopStoreService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "门店管理")
@RestController
@RequestMapping("/shop/store")
@RequiredArgsConstructor
public class ShopStoreController {

    private final ShopStoreService shopStoreService;

    @GetMapping("/list")
    @ApiOperation(value = "获取门店列表")
    public Result<IPage<ShopStore>> list(ShopStore store) {
        return Result.success(shopStoreService.selectPage(store));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的门店选项（供商品表单下拉使用，不分页）")
    public Result<List<ShopStore>> options() {
        return Result.success(shopStoreService.listOptions());
    }

    @GetMapping("/my")
    @ApiOperation(value = "获取当前登录用户担任店长的门店（不是门店用户时为空列表）")
    public Result<List<ShopStore>> my() {
        return Result.success(shopStoreService.myStores());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取门店详情")
    public Result<ShopStore> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopStoreService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加门店")
    @OperationLogger("添加门店")
    @SaCheckPermission("shop:store:add")
    public Result<Long> add(@RequestBody ShopStore store) {
        shopStoreService.insert(store);
        // save() 会把自增主键回填到入参实体
        return Result.success(store.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改门店")
    @OperationLogger("修改门店")
    @SaCheckPermission("shop:store:update")
    public Result<Void> edit(@RequestBody ShopStore store) {
        shopStoreService.update(store);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除门店")
    @OperationLogger("删除门店")
    @SaCheckPermission("shop:store:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopStoreService.deleteByIds(ids);
        return Result.success();
    }
}
