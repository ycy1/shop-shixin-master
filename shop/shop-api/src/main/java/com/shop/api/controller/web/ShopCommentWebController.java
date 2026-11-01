package com.shop.api.controller.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopCommentReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
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
 * @title ShopCommentWebController
 * @description 评论区 Web 接口（商城前台读某一对象名下的评论）
 */
@Api(tags = "评论 Web 接口")
@RestController
@RequestMapping("/shop/comment")
@RequiredArgsConstructor
public class ShopCommentWebController extends BaseWebController {

    private final ShopCommentReadService shopCommentReadService;

    /**
     * 评论区那一页：某一对象名下的顶层评论分页，每行把它名下的回复一并带回。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/comment/list。
     * 不放 /api/comm 那一包：comm 收的是「两端同一个动作」（发表、点赞、删自己那一条确实两端共用，挂在那儿），
     * 读这一段 Web 前台和 App 各有一条自己的，只是都转手到同一份 service —— 判据不能分叉。
     *
     * 参数三格：commentType + businessId 两个一起点才查（一列指三张表，id 会撞号），sortType 可选。
     * 分页吃查询串里的 pageNum/pageSize，由 PageUtil 直接从请求里取，和后台列表同一套。
     * 不要求登录：/api/** 不过 Sa-Token 拦截器，游客照常能看，登录时多带回「我点过赞没」那一格。
     */
    @GetMapping("/list")
    @ApiOperation(value = "评论列表（顶层分页 + 每行的回复，可按点赞数排；置顶那几条永远打头，无需登录）")
    public Result<IPage<CommentListVo>> list(ShopCommentQueryDto query) {
        return Result.success(shopCommentReadService.page(query));
    }
}
