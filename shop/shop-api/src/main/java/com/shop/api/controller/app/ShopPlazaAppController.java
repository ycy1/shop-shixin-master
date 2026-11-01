package com.shop.api.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopPlazaReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.dto.plaza.ShopPlazaQueryDto;
import com.shop.entity.ShopPlazaMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopPlazaAppController
 * @description 广场 App 端接口（uni-app 那一页广场 bar 和详情）
 */
@Api(tags = "广场 App 端接口")
@RestController
@RequestMapping("/shop/plaza")
@RequiredArgsConstructor
public class ShopPlazaAppController extends BaseAppController {

    private final ShopPlazaReadService shopPlazaReadService;

    /**
     * 广场那一页：读法、判据、分页参数与 Web 端逐条相同，所以直接复用同一份 service 和那份实体 ——
     * 两端看到的必须是同一份消息，判据分叉只会变成「小程序比网页少几条」这类查不动的账。
     *
     * 路径：继承 BaseAppController 会被 WebMvcConfig 加上 /api/app 前缀，即 GET /api/app/shop/plaza/list。
     */
    @GetMapping("/list")
    @ApiOperation(value = "广场消息列表（同 Web 端那一条：只看已发布且未下架的，置顶打头，无需登录）")
    public Result<IPage<ShopPlazaMessage>> list(ShopPlazaQueryDto query) {
        return Result.success(shopPlazaReadService.page(query));
    }

    /**
     * 我自己发的那些（App「我的」页里「广场消息」那一档）：读法、判据、分页参数与 Web 端逐条相同。
     *
     * 路径：GET /api/app/shop/plaza/my/list。
     */
    @GetMapping("/my/list")
    @ApiOperation(value = "我发的广场消息列表（同 Web 端那一条：含待审核与已拒绝的，需登录）")
    public Result<IPage<ShopPlazaMessage>> myList(ShopPlazaQueryDto query) {
        return Result.success(shopPlazaReadService.myPage(query));
    }

    /**
     * 一条广场消息（带正文），判据同 Web 端那一条。
     *
     * 路径：GET /api/app/shop/plaza/{id}。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "广场消息详情（同 Web 端那一条：含正文，未发布的只有作者本人看得得到）")
    public Result<ShopPlazaMessage> detail(@PathVariable("id") Long id) {
        return Result.success(shopPlazaReadService.detail(id));
    }
}
