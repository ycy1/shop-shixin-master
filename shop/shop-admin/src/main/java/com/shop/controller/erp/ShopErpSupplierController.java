package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpSupplier;
import com.shop.service.ShopErpSupplierService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "供应商管理")
@RestController
@RequestMapping("/erp/supplier")
@RequiredArgsConstructor
public class ShopErpSupplierController {

    private final ShopErpSupplierService shopErpSupplierService;

    @GetMapping("/list")
    @ApiOperation(value = "获取供应商列表")
    public Result<IPage<ShopErpSupplier>> list(ShopErpSupplier supplier) {
        return Result.success(shopErpSupplierService.selectPage(supplier));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的供应商选项（供采购单表单下拉使用，不分页）")
    public Result<List<ShopErpSupplier>> options() {
        return Result.success(shopErpSupplierService.listOptions());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取供应商详情")
    public Result<ShopErpSupplier> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpSupplierService.selectSupplierById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加供应商")
    @OperationLogger("添加供应商")
    @SaCheckPermission("erp:supplier:add")
    public Result<Long> add(@RequestBody ShopErpSupplier supplier) {
        shopErpSupplierService.insert(supplier);
        // save() 会把自增主键回填到入参实体
        return Result.success(supplier.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改供应商")
    @OperationLogger("修改供应商")
    @SaCheckPermission("erp:supplier:update")
    public Result<Void> edit(@RequestBody ShopErpSupplier supplier) {
        shopErpSupplierService.update(supplier);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除供应商")
    @OperationLogger("删除供应商")
    @SaCheckPermission("erp:supplier:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpSupplierService.deleteByIds(ids);
        return Result.success();
    }
}
