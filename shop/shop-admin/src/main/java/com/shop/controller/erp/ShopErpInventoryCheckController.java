package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryCheck;
import com.shop.service.ShopErpInventoryCheckService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "库存盘点管理")
@RestController
@RequestMapping("/erp/inventoryCheck")
@RequiredArgsConstructor
public class ShopErpInventoryCheckController {

    private final ShopErpInventoryCheckService shopErpInventoryCheckService;

    @GetMapping("/list")
    @ApiOperation(value = "获取盘点单列表（不带明细，带盘点仓库名与它现在的状态、已调整行数汇总）")
    public Result<IPage<ShopErpInventoryCheck>> list(ShopErpInventoryCheck check) {
        return Result.success(shopErpInventoryCheckService.selectPage(check));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取盘点单详情（含明细，明细带这条规格在盘点仓现在的库存数和可用库存）")
    public Result<ShopErpInventoryCheck> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpInventoryCheckService.selectCheckById(id));
    }

    @GetMapping("/skuOptions")
    @ApiOperation(value = "盘点明细的规格候选（按盘点仓库取该仓的库存行，一行是一条规格，账面 0 件的也在）")
    public Result<List<ShopErpInventory>> skuOptions(@RequestParam Long warehouseId,
            @RequestParam(required = false) String keyword) {
        return Result.success(shopErpInventoryCheckService.selectSkuOptions(warehouseId, keyword));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加盘点单（主表和明细一起提交；账面数与成本价按盘点仓快照，建完这家仓置为盘点中）")
    @OperationLogger("添加盘点单")
    @SaCheckPermission("erp:inventoryCheck:add")
    public Result<Long> add(@RequestBody ShopErpInventoryCheck check) {
        shopErpInventoryCheckService.insert(check);
        // save() 会把自增主键回填到入参实体
        return Result.success(check.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改盘点单（录实盘、改盘点范围；明细按规格原行覆盖，已调整的行不许动，盘点仓不许换）")
    @OperationLogger("修改盘点单")
    @SaCheckPermission("erp:inventoryCheck:update")
    public Result<Void> edit(@RequestBody ShopErpInventoryCheck check) {
        shopErpInventoryCheckService.update(check);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除盘点单（明细跟着一起删；只有待审核能删，删完把盘点仓腾出来）")
    @OperationLogger("删除盘点单")
    @SaCheckPermission("erp:inventoryCheck:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpInventoryCheckService.deleteByIds(ids);
        return Result.success();
    }

    @PutMapping("/audit/{id}")
    @ApiOperation(value = "审核盘点单（0 待审核 → 1 盘点中，审核过才开始录实盘、才允许调整）")
    @OperationLogger("审核盘点单")
    @SaCheckPermission("erp:inventoryCheck:audit")
    public Result<Void> audit(@PathVariable("id") Long id) {
        shopErpInventoryCheckService.audit(id);
        return Result.success();
    }

    @PutMapping("/adjust")
    @ApiOperation(value = "确认调整（按明细的差异数直接动盘点仓的库存并记 7 盘盈 / 8 盘亏 流水，可多次调；全部行调完置 2 已完成并把仓库复位成启用）")
    @OperationLogger("盘点单确认调整")
    @SaCheckPermission("erp:inventoryCheck:adjust")
    public Result<Void> adjust(@RequestBody ShopErpInventoryCheck check) {
        shopErpInventoryCheckService.adjust(check);
        return Result.success();
    }

    @PutMapping("/cancel/{id}")
    @ApiOperation(value = "取消盘点单（0/1 → 3 已取消；只要有一行已经调整过库存就不许取消）")
    @OperationLogger("取消盘点单")
    @SaCheckPermission("erp:inventoryCheck:cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        shopErpInventoryCheckService.cancel(id);
        return Result.success();
    }
}
