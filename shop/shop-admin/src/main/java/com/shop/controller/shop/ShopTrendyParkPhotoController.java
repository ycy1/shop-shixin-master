package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopTrendyParkPhoto;
import com.shop.service.ShopTrendyParkPhotoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author xxj
 * @title ShopTrendyParkPhotoController
 * @description 园区照片管理（主题公园下那一层，一张照片一行）
 *
 * 审核那一改走的就是「修改」这一条（状态是行上的一格，不另开一颗权限）；
 * 前台园区详情读的是 shop-api 那份潮玩专区读接口，只出审核通过的那几行，不从这一个口子出。
 */
@Api(tags = "园区照片管理")
@RestController
@RequestMapping("/shop/parkPhoto")
@RequiredArgsConstructor
public class ShopTrendyParkPhotoController {

    private final ShopTrendyParkPhotoService shopTrendyParkPhotoService;

    @GetMapping("/list")
    @ApiOperation(value = "获取照片列表（parkId/sourceType/status/isFeatured 精确筛，keyword 搜描述和标签）")
    public Result<IPage<ShopTrendyParkPhoto>> list(ShopTrendyParkPhoto photo) {
        return Result.success(shopTrendyParkPhotoService.selectPage(photo));
    }

    @GetMapping("/tagOptions")
    @ApiOperation(value = "标签候选（读的是 Redis 里那一串词表，不是字典也不按当前列表数；那一串空着时先从库里已在用的标签灌一遍）")
    public Result<List<String>> tagOptions() {
        return Result.success(shopTrendyParkPhotoService.listTagOptions());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取照片详情")
    public Result<ShopTrendyParkPhoto> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopTrendyParkPhotoService.detail(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加照片（所属 IP 按园区带过来；官方上传不给状态就默认审核通过）")
    @OperationLogger("添加园区照片")
    @SaCheckPermission("shop:parkPhoto:add")
    public Result<Long> add(@RequestBody ShopTrendyParkPhoto photo) {
        shopTrendyParkPhotoService.insert(photo);
        // save() 会把自增主键回填到入参实体
        return Result.success(photo.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改照片（含审核：把 status 改成 1 通过 / 2 拒绝 / 3 已隐藏）")
    @OperationLogger("修改园区照片")
    @SaCheckPermission("shop:parkPhoto:update")
    public Result<Void> edit(@RequestBody ShopTrendyParkPhoto photo) {
        shopTrendyParkPhotoService.update(photo);
        return Result.success();
    }

    @PutMapping("/audit/{id}")
    @ApiOperation(value = "审核照片（状态 + 审核意见两格一起推：1通过 2拒绝 3已隐藏）")
    @OperationLogger("审核园区照片")
    @SaCheckPermission("shop:parkPhoto:update")
    public Result<Void> audit(@PathVariable("id") Long id,
                              @RequestParam("status") Integer status,
                              @RequestParam(value = "auditMsg", required = false) String auditMsg) {
        shopTrendyParkPhotoService.audit(id, status, auditMsg);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除照片")
    @OperationLogger("删除园区照片")
    @SaCheckPermission("shop:parkPhoto:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopTrendyParkPhotoService.deleteByIds(ids);
        return Result.success();
    }
}
