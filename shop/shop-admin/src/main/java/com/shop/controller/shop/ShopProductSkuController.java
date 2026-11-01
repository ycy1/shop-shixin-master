package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.product.ShopProductSkuQueryDto;
import com.shop.dto.product.ShopProductSkuSaveDto;
import com.shop.service.ShopProductSkuService;
import com.shop.vo.product.ShopProductSkuVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品SKU（商品列表那颗「SKU管理」按钮，和它里面添加/修改/删除那三颗操作）
 *
 * 权限单开三颗（shop:product:addSku / updateSku / deleteSku），不复用商品的 add/update/delete：
 * 商品的「修改」改的是整件商品那一行，这一页改的是某个规格的价格与参数，勾了前者不等于能动后者。
 * 两个读接口不验权限 —— 点得开商品页就读得到它的规格，和商品列表同一口径。
 */
@Api(tags = "商品SKU管理")
@RestController
@RequestMapping("/shop/productSku")
@RequiredArgsConstructor
public class ShopProductSkuController {

    private final ShopProductSkuService shopProductSkuService;

    @GetMapping("/list")
    @ApiOperation(value = "获取商品SKU列表")
    public Result<IPage<ShopProductSkuVo>> list(ShopProductSkuQueryDto query) {
        return Result.success(shopProductSkuService.selectPage(query));
    }

    @GetMapping("/byProduct/{productId}")
    @ApiOperation(value = "获取某个商品下的全部SKU")
    public Result<List<ShopProductSkuVo>> byProduct(@PathVariable("productId") Long productId) {
        return Result.success(shopProductSkuService.selectByProductId(productId));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加商品SKU")
    @OperationLogger("添加商品SKU")
    @SaCheckPermission("shop:product:addSku")
    public Result<Void> add(@RequestBody ShopProductSkuSaveDto form) {
        shopProductSkuService.insert(form);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改商品SKU")
    @OperationLogger("修改商品SKU")
    @SaCheckPermission("shop:product:updateSku")
    public Result<Void> edit(@RequestBody ShopProductSkuSaveDto form) {
        shopProductSkuService.update(form);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除商品SKU")
    @OperationLogger("删除商品SKU")
    @SaCheckPermission("shop:product:deleteSku")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopProductSkuService.deleteByIds(ids);
        return Result.success();
    }
}
