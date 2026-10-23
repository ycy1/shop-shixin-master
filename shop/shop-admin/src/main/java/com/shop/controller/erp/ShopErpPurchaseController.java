package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpPurchase;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopSupplierProduct;
import com.shop.service.ShopErpPurchaseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "采购单管理")
@RestController
@RequestMapping("/erp/purchase")
@RequiredArgsConstructor
public class ShopErpPurchaseController {

    private final ShopErpPurchaseService shopErpPurchaseService;

    @GetMapping("/list")
    @ApiOperation(value = "获取采购单列表（不带明细；ERP操作员账号只看自己负责的）")
    public Result<IPage<ShopErpPurchase>> list(ShopErpPurchase purchase) {
        return Result.success(shopErpPurchaseService.selectPage(purchase));
    }

    @GetMapping("/productOptions")
    @ApiOperation(value = "获取采购明细的商品候选（＝该供应商合作中的供货商品，来自 shop_supplier_product）")
    public Result<List<ShopSupplierProduct>> productOptions(@RequestParam("supplierId") Long supplierId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopErpPurchaseService.selectProductOptions(supplierId, keyword));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取采购单详情（含明细）")
    public Result<ShopErpPurchase> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpPurchaseService.selectPurchaseById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加采购单（主表和明细一起提交）")
    @OperationLogger("添加采购单")
    @SaCheckPermission("erp:purchase:add")
    public Result<Long> add(@RequestBody ShopErpPurchase purchase) {
        shopErpPurchaseService.insert(purchase);
        // save() 会把自增主键回填到入参实体
        return Result.success(purchase.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改采购单（明细整单重存）")
    @OperationLogger("修改采购单")
    @SaCheckPermission("erp:purchase:update")
    public Result<Void> edit(@RequestBody ShopErpPurchase purchase) {
        shopErpPurchaseService.update(purchase);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除采购单（明细跟着一起删）")
    @OperationLogger("删除采购单")
    @SaCheckPermission("erp:purchase:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpPurchaseService.deleteByIds(ids);
        return Result.success();
    }

    // 「采购退货」这一路：先取草稿弹窗看（能退哪些货、每行还能退多少），点确定才落一张待出库的退货单，
    // 是两步式弹窗（同「供应商品」那套）。两颗都走 erp:purchase:return 这一棵权限
    // （改完权限要重新登录才看得到、才调得动）
    @GetMapping("/{id}/returnDraft")
    @ApiOperation(value = "取采购退货的草稿（不落库：这张采购单能退哪些货、每行还能退多少）")
    @SaCheckPermission("erp:purchase:return")
    public Result<ShopErpSaleOutbound> returnDraft(@PathVariable("id") Long id) {
        return Result.success(shopErpPurchaseService.purchaseReturnDraft(id));
    }

    @PostMapping("/{id}/return")
    @ApiOperation(value = "提交采购退货（建一张来源=3 采购退货 的出库单，待出库；可多次退）")
    @OperationLogger("采购退货")
    @SaCheckPermission("erp:purchase:return")
    public Result<Long> createReturn(@PathVariable("id") Long id, @RequestBody ShopErpSaleOutbound form) {
        return Result.success(shopErpPurchaseService.createPurchaseReturn(id, form));
    }
}
