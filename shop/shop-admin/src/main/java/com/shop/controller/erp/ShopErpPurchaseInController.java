package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpPurchaseIn;
import com.shop.entity.ShopErpPurchaseItem;
import com.shop.service.ShopErpPurchaseInService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品入库管理")
@RestController
@RequestMapping("/erp/purchaseIn")
@RequiredArgsConstructor
public class ShopErpPurchaseInController {

    private final ShopErpPurchaseInService shopErpPurchaseInService;

    @GetMapping("/list")
    @ApiOperation(value = "获取入库单列表（不带明细）")
    public Result<IPage<ShopErpPurchaseIn>> list(ShopErpPurchaseIn purchaseIn) {
        return Result.success(shopErpPurchaseInService.selectPage(purchaseIn));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取入库单详情（含明细）")
    public Result<ShopErpPurchaseIn> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpPurchaseInService.selectPurchaseInById(id));
    }

    @GetMapping("/byPurchase/{purchaseId}")
    @ApiOperation(value = "获取一张采购单名下的入库单（每张连明细一起返回，给采购单页的「入库单」弹窗用）")
    public Result<List<ShopErpPurchaseIn>> byPurchase(@PathVariable("purchaseId") Long purchaseId) {
        return Result.success(shopErpPurchaseInService.selectPurchaseInByPurchase(purchaseId));
    }

    @GetMapping("/purchaseItems/{purchaseId}")
    @ApiOperation(value = "选采购单时带出的入库明细候选（每行给可入库数量：采购数量扣掉这张单下其它入库单已登记的量）")
    public Result<List<ShopErpPurchaseItem>> purchaseItems(@PathVariable("purchaseId") Long purchaseId,
            @RequestParam(required = false) Long excludeReceiptId) {
        return Result.success(shopErpPurchaseInService.selectPurchaseItems(purchaseId, excludeReceiptId));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加入库单（主表和明细一起提交；供应商按采购单回填）")
    @OperationLogger("添加入库单")
    @SaCheckPermission("erp:purchaseIn:add")
    public Result<Long> add(@RequestBody ShopErpPurchaseIn purchaseIn) {
        shopErpPurchaseInService.insert(purchaseIn);
        // save() 会把自增主键回填到入参实体
        return Result.success(purchaseIn.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改入库单（明细整单重存）")
    @OperationLogger("修改入库单")
    @SaCheckPermission("erp:purchaseIn:update")
    public Result<Void> edit(@RequestBody ShopErpPurchaseIn purchaseIn) {
        shopErpPurchaseInService.update(purchaseIn);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除入库单（明细跟着一起删；已经入过货的单子不给删）")
    @OperationLogger("删除入库单")
    @SaCheckPermission("erp:purchaseIn:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpPurchaseInService.deleteByIds(ids);
        return Result.success();
    }

    @PutMapping("/receive")
    @ApiOperation(value = "执行入库（按明细的本次入库数加库存、刷成本价，并回写这单和采购单的已入库数量与状态）")
    @OperationLogger("入库单确认入库")
    @SaCheckPermission("erp:purchaseIn:receive")
    public Result<Void> receive(@RequestBody ShopErpPurchaseIn purchaseIn) {
        shopErpPurchaseInService.receive(purchaseIn);
        return Result.success();
    }
}
