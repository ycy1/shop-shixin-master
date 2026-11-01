package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopTrendyBox;
import com.shop.entity.ShopTrendyBoxUser;
import com.shop.service.ShopTrendyBoxService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "盲盒管理")
@RestController
@RequestMapping("/shop/box")
@RequiredArgsConstructor
public class ShopTrendyBoxController {

    private final ShopTrendyBoxService shopTrendyBoxService;

    @GetMapping("/list")
    @ApiOperation(value = "获取盲盒列表")
    public Result<IPage<ShopTrendyBox>> list(ShopTrendyBox box) {
        return Result.success(shopTrendyBoxService.selectPage(box));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取盲盒详情")
    public Result<ShopTrendyBox> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopTrendyBoxService.getById(id));
    }

    @GetMapping("/record/list")
    @ApiOperation(value = "分页查抽取记录（列表里那棵「抽取记录」用；boxId/userId/isHidden 都可空）")
    public Result<IPage<ShopTrendyBoxUser>> records(ShopTrendyBoxUser query) {
        return Result.success(shopTrendyBoxService.pageRecords(query));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加盲盒")
    @OperationLogger("添加盲盒")
    @SaCheckPermission("shop:box:add")
    public Result<Long> add(@RequestBody ShopTrendyBox box) {
        shopTrendyBoxService.insert(box);
        // save() 会把自增主键回填到入参实体
        return Result.success(box.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改盲盒")
    @OperationLogger("修改盲盒")
    @SaCheckPermission("shop:box:update")
    public Result<Void> edit(@RequestBody ShopTrendyBox box) {
        shopTrendyBoxService.update(box);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除盲盒（连带删除它名下的款式）")
    @OperationLogger("删除盲盒")
    @SaCheckPermission("shop:box:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopTrendyBoxService.deleteByIds(ids);
        return Result.success();
    }
}
