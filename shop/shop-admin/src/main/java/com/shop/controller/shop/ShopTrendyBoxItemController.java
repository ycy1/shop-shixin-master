package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopTrendyBoxItem;
import com.shop.service.ShopTrendyBoxItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 盲盒商品（款式）
 *
 * 挂在 /shop/box/item 下面，写操作的权限沿用「盲盒修改」那一串（shop:box:update）——
 * 款式是盲盒的从属数据，能改这只盒就能改它里面的款式，不再单开三条 BUTTON。
 */
@Api(tags = "盲盒商品管理")
@RestController
@RequestMapping("/shop/box/item")
@RequiredArgsConstructor
public class ShopTrendyBoxItemController {

    private final ShopTrendyBoxItemService shopTrendyBoxItemService;

    @GetMapping("/list")
    @ApiOperation(value = "获取某只盲盒名下的款式（不分页）")
    public Result<List<ShopTrendyBoxItem>> list(@RequestParam("boxId") Long boxId) {
        return Result.success(shopTrendyBoxItemService.listByBox(boxId));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加款式")
    @OperationLogger("添加盲盒款式")
    @SaCheckPermission("shop:box:update")
    public Result<Long> add(@RequestBody ShopTrendyBoxItem item) {
        shopTrendyBoxItemService.insert(item);
        return Result.success(item.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改款式")
    @OperationLogger("修改盲盒款式")
    @SaCheckPermission("shop:box:update")
    public Result<Void> edit(@RequestBody ShopTrendyBoxItem item) {
        shopTrendyBoxItemService.update(item);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除款式")
    @OperationLogger("删除盲盒款式")
    @SaCheckPermission("shop:box:update")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopTrendyBoxItemService.deleteByIds(ids);
        return Result.success();
    }
}
