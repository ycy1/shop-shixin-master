package com.shop.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.comment.ShopCommentQueryDto;
import com.shop.vo.comment.CommentListVo;

/**
 * @author ycy
 * @title ShopCommentReadService
 * @description 前台评论区的读接口（商品/门店/品牌三处详情页共用，Web 与 App 两端也共用这一份）
 */
public interface ShopCommentReadService {

    /**
     * 某一对象名下的评论：按顶层评论分页，每一行把它名下的回复一并带回。
     *
     * 不要求登录 —— 评论区是详情页内容的一部分，游客照样要能看；登录时多带回「我点过赞没」那一格。
     */
    IPage<CommentListVo> page(ShopCommentQueryDto query);
}
