package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.trendy.ShopTrendyBindDto;
import com.shop.entity.ShopTrendy;
import com.shop.service.ShopTrendyService;
import com.shop.vo.trendy.ShopTrendyCollectItemVo;
import com.shop.vo.trendy.ShopTrendyCollectProgressVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "潮玩系列管理")
@RestController
@RequestMapping("/shop/trendy")
@RequiredArgsConstructor
public class ShopTrendyController {

    private final ShopTrendyService shopTrendyService;

    @GetMapping("/list")
    @ApiOperation(value = "获取潮玩系列列表")
    public Result<IPage<ShopTrendy>> list(ShopTrendy trendy) {
        return Result.success(shopTrendyService.selectPage(trendy));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的潮玩系列选项（供商品/品牌/门店表单下拉使用，不分页）")
    public Result<List<ShopTrendy>> options() {
        return Result.success(shopTrendyService.listOptions());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取潮玩系列详情")
    public Result<ShopTrendy> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopTrendyService.getById(id));
    }

    @GetMapping("/collect/progress")
    @ApiOperation(value = "某条潮玩系列的集齐进度（抽过这条系列商品规格的人一人一行：凑到几个/这套一共几个/那笔奖励领过没有）")
    public Result<IPage<ShopTrendyCollectProgressVo>> collectProgress(
            @RequestParam("trendyId") Long trendyId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopTrendyService.collectProgress(trendyId, keyword));
    }

    @GetMapping("/collect/items")
    @ApiOperation(value = "某条系列上某个人的款式明细（这条系列下的全部规格，一格上标出他抽到过没有）")
    public Result<List<ShopTrendyCollectItemVo>> collectItems(@RequestParam("trendyId") Long trendyId,
                                                              @RequestParam("userId") Long userId) {
        return Result.success(shopTrendyService.collectItems(trendyId, userId));
    }

    @PutMapping("/product/bind")
    @ApiOperation(value = "把一批商品绑到某个潮玩系列上（trendyId 传 null 就是解绑）")
    @OperationLogger("潮玩绑定商品")
    @SaCheckPermission("shop:trendy:update")
    public Result<Void> bindProducts(@RequestBody ShopTrendyBindDto form) {
        shopTrendyService.bindProducts(form.getProductIds(), form.getTrendyId());
        return Result.success();
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加潮玩系列")
    @OperationLogger("添加潮玩系列")
    @SaCheckPermission("shop:trendy:add")
    public Result<Long> add(@RequestBody ShopTrendy trendy) {
        shopTrendyService.insert(trendy);
        // save() 会把自增主键回填到入参实体
        return Result.success(trendy.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改潮玩系列")
    @OperationLogger("修改潮玩系列")
    @SaCheckPermission("shop:trendy:update")
    public Result<Void> edit(@RequestBody ShopTrendy trendy) {
        shopTrendyService.update(trendy);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除潮玩系列")
    @OperationLogger("删除潮玩系列")
    @SaCheckPermission("shop:trendy:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopTrendyService.deleteByIds(ids);
        return Result.success();
    }
}
