package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryLog;
import com.shop.service.ShopErpInventoryService;
import com.shop.service.ShopErpInventoryLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商品库存
 *
 * 只有查和改：没有 /add（库存行由入库动作自动建）、没有 /delete（删一行等于把已入库的数量凭空抹掉，
 * 而且软删的行还占着 (product_id, warehouse_id) 唯一键，以后这个组合再入库会撞键）。
 * 库存数量的唯一入口是商品入库那边的「入库」动作，见 ShopErpPurchaseInController#receive。
 *
 * /log/list 是这一行账本的流水明细（库存日志）：同样只有查，流水由入库动作在服务层写，
 * 不在这儿开写入口。
 */
@Api(tags = "商品库存管理")
@RestController
@RequestMapping("/erp/inventory")
@RequiredArgsConstructor
public class ShopErpInventoryController {

    private final ShopErpInventoryService shopErpInventoryService;

    private final ShopErpInventoryLogService shopErpInventoryLogService;

    @GetMapping("/list")
    @ApiOperation(value = "获取库存列表（可按商品/仓库筛，也可只看低于安全库存的行）")
    public Result<IPage<ShopErpInventory>> list(ShopErpInventory inventory) {
        return Result.success(shopErpInventoryService.selectPage(inventory));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取库存详情")
    public Result<ShopErpInventory> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpInventoryService.selectInventoryById(id));
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改库存行（只放开成本价和三个预警参数，库存数量不认请求体）")
    @OperationLogger("修改商品库存")
    @SaCheckPermission("erp:inventory:update")
    public Result<Void> edit(@RequestBody ShopErpInventory inventory) {
        shopErpInventoryService.update(inventory);
        return Result.success();
    }

    @GetMapping("/log/list")
    @ApiOperation(value = "获取库存流水（按商品/仓库看一行账的进出明细，只读）")
    public Result<IPage<ShopErpInventoryLog>> logList(ShopErpInventoryLog inventoryLog) {
        return Result.success(shopErpInventoryLogService.selectPage(inventoryLog));
    }
}
