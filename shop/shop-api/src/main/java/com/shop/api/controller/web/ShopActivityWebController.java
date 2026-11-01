package com.shop.api.controller.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.vo.activity.ShopActivityDetailWebVo;
import com.shop.vo.activity.ShopActivityWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopActivityWebController
 * @description 活动 Web 接口（商城前台的活动列表页与活动详情页）
 */
@Api(tags = "活动 Web 接口")
@RestController
@RequestMapping("/shop/activity")
@RequiredArgsConstructor
public class ShopActivityWebController extends BaseWebController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 头部菜单「活动」点进去那一页：所有正在投放的活动，分页。
     *
     * 搜索结果页那个「活动」tab 也读这一条，只是多带一个 keyword（名称或标题命中都行）——
     * 判据没有第二套，那个 tab 不该比 /activity 那一页多出「只搜得到配了图的」这类暗坑。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/activity/list
     * （和商品那条 /api/web/shop/product/list 同形，分页吃查询串里的 pageNum/pageSize，由 PageUtil 取）。
     * 首页那一块轮播还留在 /api/web/shop/search/activities 没搬：它按活动位收过一道、还要求配了图，
     * 和这一页的判据不是一条，前台那处调用方也已经跑通了。
     *
     * 埋点不在这两条上 —— 曝光/点击是两端共用的动作，走 POST /api/comm/activity/track。
     */
    @GetMapping("/list")
    @ApiOperation(value = "活动列表（只给投放中的，按权重排，可按名称/标题筛，分页，无需登录）")
    public Result<IPage<ShopActivityWebVo>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopSearchReadService.pageActivities(keyword));
    }

    /**
     * 单条活动的详情：这一条是全站唯一把 content（后台富文本）递给前台的端点，
     * 页面渲染前还要过一道白名单（shop-web/src/lib/richtext.ts），两道都在才谈得上安全。
     *
     * 路径：GET /api/web/shop/activity/{id}。没在投放中的（撤下了、结束了、根本不存在）回同一句话，
     * 不从错误信息里试哪个 id 存在过 —— 同门店/品牌/商品详情那条口径。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "活动详情（只给投放中的，查不到即报错，无需登录）")
    public Result<ShopActivityDetailWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.activityDetail(id));
    }
}
