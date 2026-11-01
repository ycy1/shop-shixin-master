package com.shop.api.controller.app;

import com.shop.api.service.ShopCartReadService;
import com.shop.api.service.ShopCartWriteService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.dto.cart.ShopCartSaveDto;
import com.shop.vo.cart.ShopCartVo;
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
 * @author ycy
 * @title ShopCartAppController
 * @description 购物车 App 端接口（小程序 / App：加入购物车、车里的数量、勾选、全选、删除）
 */
@Api(tags = "购物车 App 端接口")
@RestController
@RequestMapping("/shop/cart")
@RequiredArgsConstructor
public class ShopCartAppController extends BaseAppController {

    private final ShopCartReadService shopCartReadService;

    private final ShopCartWriteService shopCartWriteService;

    /**
     * 我的购物车：一行是一个规格，一次全给、不分页。
     *
     * 路径：继承 BaseAppController 会被 WebMvcConfig 加上 /api/app 前缀，即 GET /api/app/shop/cart/list。
     * 五条都复用 Web 端那两个 service，判据（能改的只有登录 id 名下那些行、不在自己名下的按不存在处理）
     * 原样继承 —— 「必须登录」由服务层第一句 requireLoginUser 当场拒掉，不靠拦截器，
     * 因为 SaTokenConfigure 把 /api/** 整体排在登录拦截之外。参数里也没有 userId 这一格。
     */
    @GetMapping("/list")
    @ApiOperation(value = "我的购物车（一行一个规格，不分页）")
    public Result<List<ShopCartVo>> list() {
        return Result.success(shopCartReadService.myCart());
    }

    /**
     * 加入购物车：只认 skuId + quantity 两格，id 和 userId 都不认。
     *
     * 路径：POST /api/app/shop/cart/add。车上已有这一条就叠数量、有软删的行就复活，所以点第二次不报错。
     * 返回不给东西：页面画法是先落库再重拉列表。
     */
    @PostMapping("/add")
    @ApiOperation(value = "加入购物车（同一规格重复点是叠数量）")
    public Result<Void> add(@RequestBody ShopCartSaveDto form) {
        shopCartWriteService.add(form);
        return Result.success();
    }

    /**
     * 改自己名下的某一行：PUT /api/app/shop/cart/update，id 在请求体里。
     *
     * 只认 quantity 和 selected 两格，都是局部更新；数量不做库存上限，真拦在下单那一刻。
     */
    @PutMapping("/update")
    @ApiOperation(value = "改数量或勾选（局部更新，只认这两格）")
    public Result<Void> update(@RequestBody ShopCartSaveDto form) {
        shopCartWriteService.update(form);
        return Result.success();
    }

    /**
     * 全选 / 取消全选：PUT /api/app/shop/cart/selectAll?selected=1（0 是全不选）。
     *
     * 一次一句 UPDATE 按人刷完名下所有行，不让前端逐行 PUT。
     */
    @PutMapping("/selectAll")
    @ApiOperation(value = "全选或取消全选自己车里的全部行")
    public Result<Void> selectAll(@RequestParam("selected") Integer selected) {
        shopCartWriteService.selectAll(selected);
        return Result.success();
    }

    /**
     * 删掉某几行：DELETE /api/app/shop/cart/delete/{ids}，ids 逗号分隔。
     *
     * 别人的 / 已删的 / 没这个 id 一律回同一句「购物车记录不存在」，有一条不全是自己的就整批不删。
     */
    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除自己的购物车记录（逻辑删除；不在自己名下的一律按不存在处理）")
    public Result<Void> remove(@PathVariable("ids") List<Long> ids) {
        shopCartWriteService.delete(ids);
        return Result.success();
    }
}
