package com.shop.api.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopProductReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.dto.product.ShopProductWebQueryDto;
import com.shop.vo.product.ShopProductWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopProductAppController
 * @description 商品 App 端接口（小程序 / App 前台读商品）
 */
@Api(tags = "商品 App 端接口")
@RestController
@RequestMapping("/shop/product")
@RequiredArgsConstructor
public class ShopProductAppController extends BaseAppController {

    private final ShopProductReadService shopProductReadService;

    /**
     * 商品列表：首页/分类页/搜索结果共用这一条。
     *
     * 路径：继承 BaseAppController 会被 WebMvcConfig 加上 /api/app 前缀，即 GET /api/app/shop/product/list。
     * 读的商品那套判据（只看已上架、分类含下级、分页吃 pageNum/pageSize）与 Web 端逐条相同，
     * 所以直接复用 ShopProductReadService 和它那份 ShopProductWebVo —— 两端看到的必须是同一份货，
     * 判据分叉只会变成「小程序比网页少几件商品」这类查不动的账。
     *
     * SaTokenConfigure 把 /api/** 整体排在登录拦截之外，这一条也不看登录态，游客照常能逛。
     */
    @GetMapping("/list")
    @ApiOperation(value = "商品列表（只看已上架的，可按关键字/分类筛，分类含下级，无需登录）")
    public Result<IPage<ShopProductWebVo>> list(ShopProductWebQueryDto query) {
        return Result.success(shopProductReadService.page(query));
    }

    /**
     * 商品详情：只有路径里这一个 id。
     *
     * 路径：GET /api/app/shop/product/{id}。下架、已删除、不存在回同一句错误，理由同 Web 端那条。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "商品详情（同样只看已上架的，查不到即报错）")
    public Result<ShopProductWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopProductReadService.detail(id));
    }
}
