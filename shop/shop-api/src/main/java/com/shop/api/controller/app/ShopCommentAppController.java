package com.shop.api.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopCommentReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.dto.comment.ShopCommentQueryDto;
import com.shop.vo.comment.CommentListVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopCommentAppController
 * @description 评论 App 端接口（小程序 / App 前台读某一对象名下的评论）
 */
@Api(tags = "评论 App 端接口")
@RestController
@RequestMapping("/shop/comment")
@RequiredArgsConstructor
public class ShopCommentAppController extends BaseAppController {

    private final ShopCommentReadService shopCommentReadService;

    /**
     * 评论区那一页：读法、判据、分页参数与 Web 端逐条相同，所以直接复用同一份 service 和那份 CommentListVo ——
     * 两端看到的必须是同一份评论，判据分叉只会变成「小程序比网页少几条」这类查不动的账。
     *
     * 路径：继承 BaseAppController 会被 WebMvcConfig 加上 /api/app 前缀，即 GET /api/app/shop/comment/list。
     */
    @GetMapping("/list")
    @ApiOperation(value = "评论列表（同 Web 端那一条：顶层分页 + 每行的回复，置顶打头，无需登录）")
    public Result<IPage<CommentListVo>> list(ShopCommentQueryDto query) {
        return Result.success(shopCommentReadService.page(query));
    }
}
