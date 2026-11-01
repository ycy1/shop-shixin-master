package com.shop.api.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
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
 * @author ycy
 * @title ShopActivityAppController
 * @description 活动 App 端接口（搜索结果页的「活动」那一 tab + 活动详情页）
 */
@Api(tags = "活动 App 端接口")
@RestController
@RequestMapping("/shop/activity")
@RequiredArgsConstructor
public class ShopActivityAppController extends BaseAppController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 搜索结果页「活动」那一 tab：投放中的活动，分页，keyword 命中名称或标题都行。
     *
     * 路径：GET /api/app/shop/activity/list?pageNum=1&pageSize=10&keyword=xx。
     * 判据和 Web 端那条 /api/web/shop/activity/list 是同一个 service 的同一个方法（只认 status=2 投放中、按权重排），
     * 这一条只搬路径。和首页轮播那条 /api/app/shop/home/activities 不是一条：那一格按活动位收过一道还要求配了图，
     * 这一条不挑图 —— 文字类活动也得搜得到，别让搜索页比首页少出东西来。
     */
    @GetMapping("/list")
    @ApiOperation(value = "活动列表（只给投放中的，按权重排，可按名称/标题筛，分页，无需登录）")
    public Result<IPage<ShopActivityWebVo>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopSearchReadService.pageActivities(keyword));
    }

    /**
     * 活动详情页那一条：全站唯一把 content（后台富文本）递出端点的接口，App 这边也是只有这一条带它。
     *
     * 路径：GET /api/app/shop/activity/{id}。页面渲染前还要过一道：uni 用的是 <rich-text>，
     * 它按自己的标签白名单建树，script、事件属性都进不去，所以不能拿 web-view 或直接拼 HTML 顶上。
     *
     * 没在投放中的（撤下了、结束了、根本没这个 id）回同一句话，不从错误信息里试哪个 id 存在过 ——
     * 同门店/品牌/商品详情那几条的口径。埋点不在这一条上，走两端共用的 POST /api/comm/activity/track。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "活动详情（只给投放中的，含富文本内容，查不到即报错，无需登录）")
    public Result<ShopActivityDetailWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.activityDetail(id));
    }
}
