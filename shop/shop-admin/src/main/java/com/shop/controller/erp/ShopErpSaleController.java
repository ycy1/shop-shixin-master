package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpSale;
import com.shop.service.ShopErpSaleService;
import com.shop.vo.product.ShopProductVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对外销售单（进销存管理 -> 销售单管理）
 *
 * 这一页只管"卖什么、卖多少、卖多少钱"；货离仓那一步在「库存管理 -> 商品出库」，
 * 那边按这张单建一张 来源=2 销售出库 的出库单（两颗接口见 ShopErpSaleOutboundController）
 */
@Api(tags = "销售单管理")
@RestController
@RequestMapping("/erp/sale")
@RequiredArgsConstructor
public class ShopErpSaleController {

    private final ShopErpSaleService shopErpSaleService;

    @GetMapping("/list")
    @ApiOperation(value = "获取销售单列表（不带明细；ERP操作员账号只看自己负责的）")
    public Result<IPage<ShopErpSale>> list(ShopErpSale sale) {
        return Result.success(shopErpSaleService.selectPage(sale));
    }

    @GetMapping("/productOptions")
    @ApiOperation(value = "获取销售明细的商品候选（＝属于这家门店、且名下有标了可销售的 SKU 的商品；选了发货仓库再按该仓可用库存收窄）")
    public Result<List<ShopProductVo>> productOptions(@RequestParam("storeId") Long storeId,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopErpSaleService.selectProductOptions(storeId, warehouseId, keyword));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取销售单详情（含明细）")
    public Result<ShopErpSale> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpSaleService.selectSaleById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加销售单（主表和明细一起提交）")
    @OperationLogger("添加销售单")
    @SaCheckPermission("erp:sale:add")
    public Result<Long> add(@RequestBody ShopErpSale sale) {
        shopErpSaleService.insert(sale);
        // save() 会把自增主键回填到入参实体
        return Result.success(sale.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改销售单（明细整单重存；「审核」「作废」也是在这里选状态）")
    @OperationLogger("修改销售单")
    @SaCheckPermission("erp:sale:update")
    public Result<Void> edit(@RequestBody ShopErpSale sale) {
        shopErpSaleService.update(sale);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除销售单（明细跟着一起删；只有待审核 / 已取消 的能删）")
    @OperationLogger("删除销售单")
    @SaCheckPermission("erp:sale:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpSaleService.deleteByIds(ids);
        return Result.success();
    }
}
