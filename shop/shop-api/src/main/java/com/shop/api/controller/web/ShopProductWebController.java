package com.shop.api.controller.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopProductReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.dto.product.ShopProductWebQueryDto;
import com.shop.vo.product.ShopProductWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author xxj
 * @title ShopProductWebController
 * @description 商品 Web 接口（商城前台读商品）
 */
@Api(tags = "商品 Web 接口")
@RestController
@RequestMapping("/shop/product")
@RequiredArgsConstructor
public class ShopProductWebController extends BaseWebController {

    private final ShopProductReadService shopProductReadService;

    /**
     * 商品列表：首页/分类页/搜索结果共用这一条。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/product/list。
     * 不放 /api/comm 那一包：comm 收的是"两端同一个动作"（下单、接单、埋点、报名那种后台和前台都会碰的写），
     * 这一条只有 Web 前台一个使用方，就是读商品。分页走 pageNum/pageSize 查询串，由 PageUtil 直接从请求里取，
     * 和后台列表同一套。
     *
     * SaTokenConfigure 把 /api/** 整体排在登录拦截之外，所以这一条不需要 @SaCheckPermission，也不看登录态 ——
     * 游客和消费者看到的是同一份货。「只看已上架」那条判据钉在 SQL 的 WHERE 上，
     * 请求参数里没有 status 这一格可以给调用方自己挑。分类传一级会把它的整棵下级一起带上。
     */
    @GetMapping("/list")
    @ApiOperation(value = "商品列表（只看已上架的，可按关键字/分类筛，分类含下级，无需登录）")
    public Result<IPage<ShopProductWebVo>> list(ShopProductWebQueryDto query) {
        return Result.success(shopProductReadService.page(query));
    }

    /**
     * 商品详情：只有路径里这一个 id，没有可挑的状态。
     *
     * 路径：GET /api/web/shop/product/{id}（前缀同上）。
     * 下架、已删除、根本不存在 —— 这三种都回同一句「商品不存在或已下架」，
     * 不为它们分档，否则调用方拿错误信息就能试探出某个 id 是存在过的。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "商品详情（同样只看已上架的，查不到即报错）")
    public Result<ShopProductWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopProductReadService.detail(id));
    }
}
