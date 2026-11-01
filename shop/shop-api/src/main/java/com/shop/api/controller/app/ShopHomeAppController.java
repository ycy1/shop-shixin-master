package com.shop.api.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.vo.activity.ShopActivityWebVo;
import com.shop.vo.brand.ShopBrandWebVo;
import com.shop.vo.category.ShopCategoryWebVo;
import com.shop.vo.store.ShopStoreWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author ycy
 * @title ShopHomeAppController
 * @description 首页 App 端接口（轮播位、门店、品牌）
 */
@Api(tags = "首页 App 端接口")
@RestController
@RequestMapping("/shop/home")
@RequiredArgsConstructor
public class ShopHomeAppController extends BaseAppController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 轮播位：首页顶部那几条活动，判据全在 service 那一句里（活动位=首页轮播、status=2 投放中、必须有图），
     * 这里不补第二条 —— 小程序和网页上看到的必须是同一批图，判差分叉只会变成「一端少两张banner」这种查不动的账。
     *
     * 路径：GET /api/app/shop/home/activities。不分页，一次给完（后台本来就只让有限的几条挂首页位）。
     */
    @GetMapping("/activities")
    @ApiOperation(value = "首页轮播位的投放中活动（不分页，无需登录）")
    public Result<List<ShopActivityWebVo>> activities() {
        return Result.success(shopSearchReadService.listHomeActivities());
    }

    /**
     * 门店：首页那一块取的是「启用中的门店」，分页吃 pageNum/pageSize 两个请求参数（PageUtil.getPage 直接读 request）。
     *
     * 路径：GET /api/app/shop/home/stores?pageSize=10。App 首页只铺一行，取前几条就够，
     * 要全量去搜索页；keyword 那一格先留着，它和 Web 端那条同源。
     */
    @GetMapping("/stores")
    @ApiOperation(value = "启用中的门店（分页，可按名称筛，无需登录）")
    public Result<IPage<ShopStoreWebVo>> stores(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopSearchReadService.pageStores(keyword));
    }

    /**
     * 品牌：同门店那一条，只是候选换成启用中的品牌。
     *
     * 路径：GET /api/app/shop/home/brands?pageSize=10。
     */
    @GetMapping("/brands")
    @ApiOperation(value = "启用中的品牌（分页，可按名称筛，无需登录）")
    public Result<IPage<ShopBrandWebVo>> brands(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopSearchReadService.pageBrands(keyword));
    }

    /**
     * 分类候选：首页「全部商品」那一行筛选用的。
     *
     * 路径：GET /api/app/shop/home/categories。一次给全、不分页（分页就把候选切没了），
     * 按「父在前、下级紧跟」铺平，层级带 level —— 形状和 Web 首页筛选栏那一条完全同源，
     * 两端切出来的商品必须是同一批。商品列表那条早就吃 categoryId，命中这一级连同整棵下级一起筛，
     * 由服务端展开（见 ShopProductReadServiceImpl.expandCategoryScope）。
     */
    @GetMapping("/categories")
    @ApiOperation(value = "分类候选列表（只给启用中的，已按父子顺序铺平，无需登录）")
    public Result<List<ShopCategoryWebVo>> categories() {
        return Result.success(shopSearchReadService.listCategories());
    }
}
