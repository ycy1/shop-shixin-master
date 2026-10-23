package com.shop.api.controller.web;

import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.vo.store.ShopStoreWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopStoreWebController
 * @description 门店 Web 接口（商城前台读单个门店）
 */
@Api(tags = "门店 Web 接口")
@RestController
@RequestMapping("/shop/store")
@RequiredArgsConstructor
public class ShopStoreWebController extends BaseWebController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 门店详情页的头部 + 那一圈图片。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/store/{id}
     * （和商品详情那条 /api/web/shop/product/{id} 同形）。门店/品牌那两条列表还留在 /shop/search 里没搬：
     * 它们是搜索结果页的两个 tab，路径改了前台两处调用方跟着返工，不值当。
     * 读的是 ShopSearchReadService —— 同一份 VO、同一档判据（只看 status=1），不为详情再开一个服务类。
     *
     * 同样不放 /api/comm（只有前台一个使用方），SaTokenConfigure 把 /api/** 排在登录拦截之外，所以不要登录态。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "门店详情（只看启用中的，查不到即报错，无需登录）")
    public Result<ShopStoreWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.storeDetail(id));
    }
}
