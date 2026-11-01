package com.shop.api.controller.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopTrendyWebReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.vo.trendy.ShopParkPhotoWebVo;
import com.shop.vo.trendy.ShopTrendyBoxWebVo;
import com.shop.vo.trendy.ShopTrendyCollectItemVo;
import com.shop.vo.trendy.ShopTrendyCollectionViewVo;
import com.shop.vo.trendy.ShopTrendyParkWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author xxj
 * @title ShopTrendyWebController
 * @description 潮玩专区 Web 接口（shop-web 左侧那条 首页/盲盒/公园 的三趟读）
 *
 * 路径：继承 BaseWebController ⇒ 前缀 /api/web，即 GET /api/web/shop/trendy/box/list 这几条，
 * 和商品、活动那两条前台读接口同形；都不过登录态（专区是给人逛的，逛不需要账号）。
 * 抽盒那个动作不在这三条里 —— 它是两端共用的写动作，走 POST /api/comm/trendy/box/{boxId}/draw。
 */
@Api(tags = "潮玩专区 Web 接口")
@RestController
@RequestMapping("/shop/trendy")
@RequiredArgsConstructor
public class ShopTrendyWebController extends BaseWebController {

    private final ShopTrendyWebReadService shopTrendyWebReadService;

    @GetMapping("/box/list")
    @ApiOperation(value = "盲盒列表（只给未开始/进行中，带系列名、款式预览和剩余次数，分页，无需登录）")
    public Result<IPage<ShopTrendyBoxWebVo>> boxList(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "hot", required = false) Integer hot) {
        return Result.success(shopTrendyWebReadService.pageBoxes(status, hot));
    }

    @GetMapping("/box/{id}")
    @ApiOperation(value = "盲盒详情（名下在架款式全给，不做预览截断；已结束/不存在回同一句话）")
    public Result<ShopTrendyBoxWebVo> boxDetail(@PathVariable("id") Long id) {
        return Result.success(shopTrendyWebReadService.boxDetail(id));
    }

    @GetMapping("/park/list")
    @ApiOperation(value = "园区列表（只给营业中的，带名下在架票种数和最低价，不分页，无需登录）")
    public Result<List<ShopTrendyParkWebVo>> parkList(
            @RequestParam(value = "hot", required = false) Integer hot) {
        return Result.success(shopTrendyWebReadService.listParks(hot));
    }

    @GetMapping("/park/{id}")
    @ApiOperation(value = "园区详情（含名下在架票种；停业/下架/不存在回同一句话）")
    public Result<ShopTrendyParkWebVo> parkDetail(@PathVariable("id") Long id) {
        return Result.success(shopTrendyWebReadService.parkDetail(id));
    }

    @GetMapping("/park/{id}/photo/list")
    @ApiOperation(value = "一家园区的全部照片（分页，只给审核通过的、精选在前；详情页顶上那一屏走详情那条）")
    public Result<IPage<ShopParkPhotoWebVo>> parkPhotos(@PathVariable("id") Long id) {
        return Result.success(shopTrendyWebReadService.pageParkPhotos(id));
    }

    /**
     * 图鉴那一页（/trendy/collection）：一行一条潮玩系列，带我的收集进度和集齐奖励，10 条一页、我抽过的排前面。
     *
     * 不登录也回（进度念「0 / N」），但认人只认登录态 —— 参数里没有 userId，
     * 能点名别人就等于让人替别人看图鉴。领奖励那颗按钮不在这条上，它是写动作，走
     * POST /api/comm/trendy/collection/{trendyId}/claim。
     */
    @GetMapping("/collection/list")
    @ApiOperation(value = "图鉴：所有潮玩系列 × 我的收集进度 + 集齐奖励（分页，收集过的排最前）")
    public Result<IPage<ShopTrendyCollectionViewVo>> collectionList() {
        return Result.success(shopTrendyWebReadService.pageCollection());
    }

    /**
     * 图鉴卡片底下那颗「看收集明细」：这一条系列名下的全部规格，一格标出我抽到过没有（不分页，一次全带）。
     *
     * 只认登录态，路径上没有 userId：能点名别人就等于让别人看别人的图鉴。
     * 没登录也回 —— 那时每一格都是「还没抽到」，这一屏照样能看整套长什么样。
     */
    @GetMapping("/collection/{trendyId}/items")
    @ApiOperation(value = "图鉴：一条系列的收集明细（全部规格 × 我抽到过没有；未登录全标没抽到）")
    public Result<List<ShopTrendyCollectItemVo>> collectionItems(@PathVariable("trendyId") Long trendyId) {
        return Result.success(shopTrendyWebReadService.listCollectionItems(trendyId));
    }
}
