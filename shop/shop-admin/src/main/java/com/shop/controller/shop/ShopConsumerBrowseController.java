package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.browse.ShopBrowseQueryDto;
import com.shop.dto.browse.ShopBrowseSaveDto;
import com.shop.service.ShopConsumerBrowseService;
import com.shop.vo.browse.ShopBrowseVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "浏览足迹管理")
@RestController
@RequestMapping("/shop/browse")
@RequiredArgsConstructor
public class ShopConsumerBrowseController {

    private final ShopConsumerBrowseService shopConsumerBrowseService;

    @GetMapping("/list")
    @ApiOperation(value = "获取浏览足迹列表")
    @SaCheckPermission("shop:browse:list")
    public Result<IPage<ShopBrowseVo>> list(ShopBrowseQueryDto query) {
        return Result.success(shopConsumerBrowseService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取浏览足迹详情")
    public Result<ShopBrowseVo> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopConsumerBrowseService.selectBrowseById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "记录浏览足迹（同一消费者同一商品只留一条，重复记录只刷新浏览时间）")
    @OperationLogger("记录浏览足迹")
    @SaCheckPermission("shop:browse:add")
    public Result<Void> add(@RequestBody ShopBrowseSaveDto form) {
        shopConsumerBrowseService.insert(form);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改浏览足迹")
    @OperationLogger("修改浏览足迹")
    @SaCheckPermission("shop:browse:update")
    public Result<Void> edit(@RequestBody ShopBrowseSaveDto form) {
        shopConsumerBrowseService.update(form);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除浏览足迹")
    @OperationLogger("删除浏览足迹")
    @SaCheckPermission("shop:browse:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopConsumerBrowseService.deleteByIds(ids);
        return Result.success();
    }
}
