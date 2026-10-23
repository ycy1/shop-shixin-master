package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpWarehouse;
import com.shop.service.ShopErpWarehouseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "仓库管理")
@RestController
@RequestMapping("/erp/warehouse")
@RequiredArgsConstructor
public class ShopErpWarehouseController {

    private final ShopErpWarehouseService shopErpWarehouseService;

    @GetMapping("/list")
    @ApiOperation(value = "获取仓库列表")
    public Result<IPage<ShopErpWarehouse>> list(ShopErpWarehouse warehouse) {
        return Result.success(shopErpWarehouseService.selectPage(warehouse));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的仓库选项（供商品入库表单下拉使用，不分页）")
    public Result<List<ShopErpWarehouse>> options() {
        return Result.success(shopErpWarehouseService.listOptions());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取仓库详情")
    public Result<ShopErpWarehouse> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpWarehouseService.selectWarehouseById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加仓库")
    @OperationLogger("添加仓库")
    @SaCheckPermission("erp:warehouse:add")
    public Result<Long> add(@RequestBody ShopErpWarehouse warehouse) {
        shopErpWarehouseService.insert(warehouse);
        // save() 会把自增主键回填到入参实体
        return Result.success(warehouse.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改仓库")
    @OperationLogger("修改仓库")
    @SaCheckPermission("erp:warehouse:update")
    public Result<Void> edit(@RequestBody ShopErpWarehouse warehouse) {
        shopErpWarehouseService.update(warehouse);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除仓库")
    @OperationLogger("删除仓库")
    @SaCheckPermission("erp:warehouse:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpWarehouseService.deleteByIds(ids);
        return Result.success();
    }
}
