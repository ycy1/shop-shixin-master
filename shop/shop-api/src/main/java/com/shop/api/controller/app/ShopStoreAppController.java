package com.shop.api.controller.app;

import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.vo.store.ShopStoreWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopStoreAppController
 * @description 门店 App 端接口（小程序 / App 前台读单个门店）
 */
@Api(tags = "门店 App 端接口")
@RestController
@RequestMapping("/shop/store")
@RequiredArgsConstructor
public class ShopStoreAppController extends BaseAppController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 门店详情页的头部 + 那一圈图片。
     *
     * 路径：继承 BaseAppController 会被 WebMvcConfig 加上 /api/app 前缀，即 GET /api/app/shop/store/{id}。
     * 和 Web 端那条 /api/web/shop/store/{id} 调的是同一个 service 的同一个方法：一份 ShopStoreWebVo、
     * 一档判据（只看启用中的店，停用 / 已删 / 没这个 id 回同一句错误）—— 两端点开同一家店看到的必须一样，
     * 所以这一条只是把路径挪到 App 那一档，不为详情再开一个服务类。
     *
     * SaTokenConfigure 把 /api/** 整体排在登录拦截之外，这一条不看登录态，游客照常能逛店。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "门店详情（只看启用中的，查不到即报错，无需登录）")
    public Result<ShopStoreWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.storeDetail(id));
    }
}
