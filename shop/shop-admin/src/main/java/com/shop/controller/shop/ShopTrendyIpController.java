package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.trendy.ShopTrendyIpBindDto;
import com.shop.entity.ShopTrendyIp;
import com.shop.service.ShopTrendyIpService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "潮玩IP管理")
@RestController
@RequestMapping("/shop/trendyIp")
@RequiredArgsConstructor
public class ShopTrendyIpController {

    private final ShopTrendyIpService shopTrendyIpService;

    @GetMapping("/list")
    @ApiOperation(value = "获取潮玩IP列表")
    public Result<IPage<ShopTrendyIp>> list(ShopTrendyIp ip) {
        return Result.success(shopTrendyIpService.selectPage(ip));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的潮玩IP选项（供系列表单「所属IP」下拉使用，不分页）")
    public Result<List<ShopTrendyIp>> options() {
        return Result.success(shopTrendyIpService.listOptions());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取潮玩IP详情")
    public Result<ShopTrendyIp> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopTrendyIpService.getById(id));
    }

    @PutMapping("/trendy/bind")
    @ApiOperation(value = "把一批潮玩系列绑到某个IP上（ipId 传 null 就是解绑）")
    @OperationLogger("潮玩IP绑定系列")
    @SaCheckPermission("shop:trendyIp:update")
    public Result<Void> bindTrendies(@RequestBody ShopTrendyIpBindDto form) {
        shopTrendyIpService.bindTrendies(form.getTrendyIds(), form.getIpId());
        return Result.success();
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加潮玩IP")
    @OperationLogger("添加潮玩IP")
    @SaCheckPermission("shop:trendyIp:add")
    public Result<Long> add(@RequestBody ShopTrendyIp ip) {
        shopTrendyIpService.insert(ip);
        // save() 会把自增主键回填到入参实体
        return Result.success(ip.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改潮玩IP")
    @OperationLogger("修改潮玩IP")
    @SaCheckPermission("shop:trendyIp:update")
    public Result<Void> edit(@RequestBody ShopTrendyIp ip) {
        shopTrendyIpService.update(ip);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除潮玩IP")
    @OperationLogger("删除潮玩IP")
    @SaCheckPermission("shop:trendyIp:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopTrendyIpService.deleteByIds(ids);
        return Result.success();
    }
}
