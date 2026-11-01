package com.shop.api.controller.web;

import com.shop.api.service.ShopSearchReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
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
 * @title ShopNoticeWebController
 * @description 公告 Web 接口（消息中心里点「公告通知【…】」跳进来那一页）
 */
@Api(tags = "公告 Web 接口")
@RestController
@RequestMapping("/shop/notice")
@RequiredArgsConstructor
public class ShopNoticeWebController extends BaseWebController {

    private final ShopSearchReadService shopSearchReadService;

    /**
     * 公告详情页那一条：全站唯一把公告正文（后台富文本 HTML）递给不要登录的前台的端点，
     * 页面渲染前还要过一道白名单（shop-web/src/lib/richtext.ts），两道都在才谈得上安全。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/notice/{id}。
     * id 就是那条消息的 businessId，前台不需要先查一遍公告是否存在。
     *
     * 判据只有 is_show=1 一道（后台上下线那颗开关）：已下线的公告留在库里给历史消息看，但这一页进不去，
     * 和不存在、删掉回同一句话 —— 不从错误信息里试哪个 id 存在过，同活动/门店/品牌详情那条口径。
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "公告详情（只给展示中的，含富文本内容，查不到即报错，无需登录）")
    public Result<NoticeDetailWebVo> detail(@PathVariable("id") Long id) {
        return Result.success(shopSearchReadService.noticeDetail(id));
    }
}
