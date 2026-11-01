package com.shop.api.controller.app;

import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.vo.brand.ShopBrandWebVo;
import com.shop.vo.store.ShopStoreWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author ycy
 * @title ShopBrandAppController
 * @description 品牌 App 端接口（小程序 / App 前台读单个品牌 + 它名下的门店）
 */
@Api(tags = "品牌 App 端接口")
@RestController
@RequestMapping("/shop/brand")
@RequiredArgsConstructor
public class ShopBrandAppController extends BaseAppController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 品牌详情页的头部 + 代言人那一圈图片。
     *
     * 路径：GET /api/app/shop/brand/{id}。判据、VO 都和 Web 端那条 /api/web/shop/brand/{id} 同源
     * （同一份 ShopSearchReadService#brandDetail，只看启用中的品牌），这一条只搬路径不搬账。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "品牌详情（只看启用中的，查不到即报错，无需登录）")
    public Result<ShopBrandWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.brandDetail(id));
    }

    /**
     * 品牌详情页的「品牌门店」那一块：哪些启用中的门店在表单里绑了这个品牌（shop_store.brand_id）。
     *
     * 路径：GET /api/app/shop/brand/{id}/stores。不接分页 —— 一个品牌名下的门店就是那么几家，
     * 页面上一排排摆完，分页反而让人以为还能翻出更多；品牌不存在也照常回空列表（头部那条已经把话说了）。
     */
    @GetMapping("/{id}/stores")
    @ApiOperation(value = "品牌名下的门店（按门店自己绑的品牌那一格取，只给启用中的店，无需登录）")
    public Result<List<ShopStoreWebVo>> stores(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.brandStores(id));
    }
}
