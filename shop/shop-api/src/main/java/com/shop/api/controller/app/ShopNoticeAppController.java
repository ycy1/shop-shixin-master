package com.shop.api.controller.app;

import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.vo.notice.NoticeDetailWebVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopNoticeAppController
 * @description 公告 App 端接口（我的消息里点公告跳进来那一页）
 */
@Api(tags = "公告 App 端接口")
@RestController
@RequestMapping("/shop/notice")
@RequiredArgsConstructor
public class ShopNoticeAppController extends BaseAppController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 公告详情页那一条：判据和 Web 端那条 /api/web/shop/notice/{id} 是同一个 service 的同一个方法
     * （只认 is_show=1，查不到回同一句话），这一条只搬路径：GET /api/app/shop/notice/{id}。
     *
     * App 这边不另做清洗：渲染用的是 <rich-text>，它按自己的标签白名单建树，script 和事件属性都进不去，
     * 所以不能拿 web-view 或直接拼 HTML 顶上（同活动详情页那条的口径）。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "公告详情（只给展示中的，含富文本内容，查不到即报错，无需登录）")
    public Result<NoticeDetailWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.noticeDetail(id));
    }
}
