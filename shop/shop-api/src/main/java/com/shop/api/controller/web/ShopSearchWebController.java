package com.shop.api.controller.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopSearchHotService;
import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.vo.activity.ShopActivityWebVo;
import com.shop.vo.brand.ShopBrandWebVo;
import com.shop.vo.category.ShopCategoryWebVo;
import com.shop.vo.store.ShopStoreWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author xxj
 * @title ShopSearchWebController
 * @description 搜索 Web 接口（商城前台按关键字找门店 / 品牌，首页筛选栏的分类候选、那一块活动轮播与那一排热搜）
 */
@Api(tags = "搜索 Web 接口")
@RestController
@RequestMapping("/shop/search")
@RequiredArgsConstructor
public class ShopSearchWebController extends BaseWebController {

    private final ShopSearchReadService shopSearchReadService;

    private final ShopSearchHotService shopSearchHotService;

    /**
     * 搜索结果页三个 tab 里门店、品牌那两块（商品那块共用 /api/web/shop/product/list，它早就吃 keyword）。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/search/stores。
     * 同样不放 /api/comm —— comm 收的是两端同一个动作，这两条只有前台一个使用方。
     * SaTokenConfigure 把 /api/** 整体排在登录拦截之外，所以不写 @SaCheckPermission、也不看登录态；
     * 分页吃查询串里的 pageNum/pageSize（PageUtil），keyword 空就是不限，页面可以直接当"浏览全部门店"用。
     */
    @GetMapping("/stores")
    @ApiOperation(value = "门店列表（只给启用中的，可按关键字搜，无需登录）")
    public Result<IPage<ShopStoreWebVo>> stores(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopSearchReadService.pageStores(keyword));
    }

    @GetMapping("/brands")
    @ApiOperation(value = "品牌列表（只给启用中的，可按名称搜，无需登录）")
    public Result<IPage<ShopBrandWebVo>> brands(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopSearchReadService.pageBrands(keyword));
    }

    /**
     * 首页「全部商品」筛选栏的分类候选。
     *
     * 吃的是筛选栏要的形状：一次给全、不分页（分页就把候选切没了），按「父在前、下级紧跟」铺平，
     * 层级带 level 给页面缩进。商品列表那条 /api/web/shop/product/list 早就吃 categoryId，
     * 命中这一级连同整棵下级一起筛，由服务端展开（见 ShopProductReadServiceImpl.expandCategoryScope）。
     */
    @GetMapping("/categories")
    @ApiOperation(value = "分类候选列表（只给启用中的，已按父子顺序铺平，无需登录）")
    public Result<List<ShopCategoryWebVo>> categories() {
        return Result.success(shopSearchReadService.listCategories());
    }

    /**
     * 首页那一块活动轮播要的那几条（判据在 ShopSearchReadServiceImpl.listHomeActivities：首页轮播位 + 投放中 + 有图）。
     *
     * 埋点不挂在这条上 —— 曝光/点击是「后台那一页也要看同一张流水」的两端动作，走的是
     * POST /api/comm/activity/track（ShopActivityCommonController），前台只管往里插行。
     */
    @GetMapping("/activities")
    @ApiOperation(value = "首页活动轮播位（只给投放中且配了图的，按权重排，无需登录）")
    public Result<List<ShopActivityWebVo>> activities() {
        return Result.success(shopSearchReadService.listHomeActivities());
    }

    /**
     * 首页搜索框下那一排热搜（Redis 那个 ZSet 里分值最高的前 10 个关键字，判据见 ShopSearchHotServiceImpl）。
     *
     * 还没人搜过就回空列表 —— 那一排整块不出现，不是报错。
     */
    @GetMapping("/hot")
    @ApiOperation(value = "热搜关键字（按被搜次数排，最多 10 条，无需登录）")
    public Result<List<String>> hot() {
        return Result.success(shopSearchHotService.listHot());
    }

    /**
     * 记一次搜索（同一个关键字一票），和上面那条读的是同一个 ZSet。
     *
     * 为什么单独一颗、不挂在 /stores / /brands 那两条读接口上：进搜索结果页那一趟，前端会按 pageSize=1
     * 给四个 tab 各探一次数，翻页还会再捞 —— 挂在读接口上就是「搜一次、计四票」，词越热就越虚。
     * 由搜索页在真正进那一页时打一次，一票就是一次搜索。
     *
     * 关键字走查询串（apiPost 发的是 JSON 体，@RequestParam 收不到体里的字段），脏值服务端自己放过、不报错。
     */
    @PostMapping("/hot")
    @ApiOperation(value = "记一次搜索（给热搜加分，无需登录）")
    public Result<Void> recordHot(@RequestParam(value = "keyword", required = false) String keyword) {
        shopSearchHotService.record(keyword);
        return Result.success();
    }
}
