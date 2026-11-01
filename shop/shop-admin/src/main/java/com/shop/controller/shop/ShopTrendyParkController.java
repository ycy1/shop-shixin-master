package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopTrendyPark;
import com.shop.service.ShopTrendyParkService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "主题公园园区管理")
@RestController
@RequestMapping("/shop/park")
@RequiredArgsConstructor
public class ShopTrendyParkController {

    private final ShopTrendyParkService shopTrendyParkService;

    @GetMapping("/list")
    @ApiOperation(value = "获取园区列表")
    public Result<IPage<ShopTrendyPark>> list(ShopTrendyPark park) {
        return Result.success(shopTrendyParkService.selectPage(park));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取营业中的园区选项（供票种表单、出票表单那格「所属园区」下拉使用，不分页）")
    public Result<List<ShopTrendyPark>> options() {
        return Result.success(shopTrendyParkService.listOptions());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取园区详情")
    public Result<ShopTrendyPark> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopTrendyParkService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加园区（编号由服务端生成）")
    @OperationLogger("添加主题公园园区")
    @SaCheckPermission("shop:park:add")
    public Result<Long> add(@RequestBody ShopTrendyPark park) {
        shopTrendyParkService.insert(park);
        // save() 会把自增主键回填到入参实体
        return Result.success(park.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改园区")
    @OperationLogger("修改主题公园园区")
    @SaCheckPermission("shop:park:update")
    public Result<Void> edit(@RequestBody ShopTrendyPark park) {
        shopTrendyParkService.update(park);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除园区（名下还有票种时拦住）")
    @OperationLogger("删除主题公园园区")
    @SaCheckPermission("shop:park:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopTrendyParkService.deleteByIds(ids);
        return Result.success();
    }
}
