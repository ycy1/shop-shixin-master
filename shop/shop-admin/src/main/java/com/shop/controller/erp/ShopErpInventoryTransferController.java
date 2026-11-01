package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryTransfer;
import com.shop.service.ShopErpInventoryTransferService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "库存调拨管理")
@RestController
@RequestMapping("/erp/inventoryTransfer")
@RequiredArgsConstructor
public class ShopErpInventoryTransferController {

    private final ShopErpInventoryTransferService shopErpInventoryTransferService;

    @GetMapping("/list")
    @ApiOperation(value = "获取调拨单列表（不带明细，带两家仓名与它们各自的状态、已调出/已调入汇总）")
    public Result<IPage<ShopErpInventoryTransfer>> list(ShopErpInventoryTransfer transfer) {
        return Result.success(shopErpInventoryTransferService.selectPage(transfer));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取调拨单详情（含明细，明细带调出仓这条规格的可用库存）")
    public Result<ShopErpInventoryTransfer> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpInventoryTransferService.selectTransferById(id));
    }

    @GetMapping("/skuOptions")
    @ApiOperation(value = "调拨明细的规格候选（按调出仓库取该仓有可用库存的那些库存行，一行是一条规格）")
    public Result<List<ShopErpInventory>> skuOptions(@RequestParam Long warehouseId,
            @RequestParam(required = false) String keyword) {
        return Result.success(shopErpInventoryTransferService.selectSkuOptions(warehouseId, keyword));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加调拨单（主表和明细一起提交；成本价按调出仓带出，建完两家仓置为调拨中）")
    @OperationLogger("添加调拨单")
    @SaCheckPermission("erp:inventoryTransfer:add")
    public Result<Long> add(@RequestBody ShopErpInventoryTransfer transfer) {
        shopErpInventoryTransferService.insert(transfer);
        // save() 会把自增主键回填到入参实体
        return Result.success(transfer.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改调拨单（明细整单重存；只有待审核能改，两家仓不许换）")
    @OperationLogger("修改调拨单")
    @SaCheckPermission("erp:inventoryTransfer:update")
    public Result<Void> edit(@RequestBody ShopErpInventoryTransfer transfer) {
        shopErpInventoryTransferService.update(transfer);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除调拨单（明细跟着一起删；只有待审核能删，删完把两家仓腾出来）")
    @OperationLogger("删除调拨单")
    @SaCheckPermission("erp:inventoryTransfer:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpInventoryTransferService.deleteByIds(ids);
        return Result.success();
    }

    @PutMapping("/audit/{id}")
    @ApiOperation(value = "审核调拨单（0 待审核 → 1 已审核，审核过才允许开始调拨）")
    @OperationLogger("审核调拨单")
    @SaCheckPermission("erp:inventoryTransfer:audit")
    public Result<Void> audit(@PathVariable("id") Long id) {
        shopErpInventoryTransferService.audit(id);
        return Result.success();
    }

    @PutMapping("/transfer")
    @ApiOperation(value = "执行调拨（按明细的本次调拨数直接动两家仓的库存并各记一行流水，可多次调；调满置 3 已完成并把两家仓复位成启用）")
    @OperationLogger("调拨单确认调拨")
    @SaCheckPermission("erp:inventoryTransfer:transfer")
    public Result<Void> transfer(@RequestBody ShopErpInventoryTransfer transfer) {
        shopErpInventoryTransferService.transfer(transfer);
        return Result.success();
    }

    @PutMapping("/cancel/{id}")
    @ApiOperation(value = "取消调拨单（0/1 → 4 已取消；这两档一笔库存都没动，取消只是作废并腾出两家仓）")
    @OperationLogger("取消调拨单")
    @SaCheckPermission("erp:inventoryTransfer:cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        shopErpInventoryTransferService.cancel(id);
        return Result.success();
    }
}
