package com.shop.api.controller.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopPlazaReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
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
 * @title ShopPlazaWebController
 * @description 广场 Web 接口（前台「广场」那一页的列表和详情，加上个人中心里「我发的」那一档）
 */
@Api(tags = "广场 Web 接口")
@RestController
@RequestMapping("/shop/plaza")
@RequiredArgsConstructor
public class ShopPlazaWebController extends BaseWebController {

    private final ShopPlazaReadService shopPlazaReadService;

    /**
     * 广场那一页：一次十条往下翻，置顶打头、按发布时间倒序。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/plaza/list。
     * 只读这一段放在 web 包（App 端有自己同形的那一条，两段共用同一份 service，判据不分叉）；
     * 发布/点赞/分享是「两端同一个动作」，挂在那条 comm 路径上。
     *
     * 分页吃查询串里的 pageNum/pageSize（由 PageUtil 直接从请求里取），前端每一翻就带下一页的页码 ——
     * 「一次请求 10 条」是前端在这两格上给的，不是这一条接口写死的。
     * 不要求登录：游客照常能逛，登录时每一行多带回「我赞过没有」那一格。
     */
    @GetMapping("/list")
    @ApiOperation(value = "广场消息列表（只看已发布且未下架的，联表带出发布者昵称/关联对象名，三个计数现算；无需登录）")
    public Result<IPage<ShopPlazaMessage>> list(ShopPlazaQueryDto query) {
        return Result.success(shopPlazaReadService.page(query));
    }

    /**
     * 我自己发的那些（个人中心「广场消息」那一档）：一次一页往下翻，按创建时间倒序。
     *
     * 路径：GET /api/web/shop/plaza/my/list（两段路径，不会被下面那条 /{id} 吃掉）。
     * 必须登录；待审核、已拒绝、被下架的都在这份列表里 —— 这一页的用处就是让人看到审到哪一步，
     * 然后把那一条改掉或删掉（改删走 /api/comm/shop/plaza/update、/plaza/delete/{id} 那两条，两端共用）。
     */
    @GetMapping("/my/list")
    @ApiOperation(value = "我发的广场消息列表（含待审核与已拒绝的，按创建时间倒序；需登录）")
    public Result<IPage<ShopPlazaMessage>> myList(ShopPlazaQueryDto query) {
        return Result.success(shopPlazaReadService.myPage(query));
    }

    /**
     * 一条广场消息（带富文本正文）：详情页和「我自己的那一条待审核的为什么没上广场」都读它。
     *
     * 路径：GET /api/web/shop/plaza/{id}。未登录也能点已发布的那一些；
     * 待审核/已拒绝/已下架的只有作者本人读得到，别人读到的还是同一句「这条消息不存在」。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "广场消息详情（含正文；未发布的那一些只有作者本人看得得到）")
    public Result<ShopPlazaMessage> detail(@PathVariable("id") Long id) {
        return Result.success(shopPlazaReadService.detail(id));
    }
}
