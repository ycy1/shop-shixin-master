package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.favorite.ShopFavoriteQueryDto;
import com.shop.dto.favorite.ShopFavoriteSaveDto;
import com.shop.service.ShopConsumerFavoriteService;
import com.shop.vo.favorite.ShopFavoriteVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "用户收藏管理")
@RestController
@RequestMapping("/shop/favorite")
@RequiredArgsConstructor
public class ShopConsumerFavoriteController {

    private final ShopConsumerFavoriteService shopConsumerFavoriteService;

    @GetMapping("/list")
    @ApiOperation(value = "获取收藏列表")
    public Result<IPage<ShopFavoriteVo>> list(ShopFavoriteQueryDto query) {
        return Result.success(shopConsumerFavoriteService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取收藏记录详情")
    public Result<ShopFavoriteVo> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopConsumerFavoriteService.selectFavoriteById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加收藏记录")
    @OperationLogger("添加收藏记录")
    @SaCheckPermission("shop:favorite:add")
    public Result<Void> add(@RequestBody ShopFavoriteSaveDto form) {
        shopConsumerFavoriteService.insert(form);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改收藏记录")
    @OperationLogger("修改收藏记录")
    @SaCheckPermission("shop:favorite:update")
    public Result<Void> edit(@RequestBody ShopFavoriteSaveDto form) {
        shopConsumerFavoriteService.update(form);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除收藏记录")
    @OperationLogger("删除收藏记录")
    @SaCheckPermission("shop:favorite:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopConsumerFavoriteService.deleteByIds(ids);
        return Result.success();
    }
}
