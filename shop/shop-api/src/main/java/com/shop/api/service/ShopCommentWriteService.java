package com.shop.api.service;

import com.shop.dto.comment.ShopCommentSaveDto;
import com.shop.entity.SysComment;

/**
 * @author ycy
 * @title ShopCommentWriteService
 * @description 前台评论区的写入（发表/回复、点赞开关、删自己那一条）—— Web 与 App 两端共用，所以端点挂在 comm 那一包
 */
public interface ShopCommentWriteService {

    /** 发一条评论，或回复某一条（replyToId 给了就是回复）；回刚落库的那一行 */
    SysComment publish(ShopCommentSaveDto form);

    /** 点赞那颗按钮：点过就取消、没点过就点上，回按完之后的状态 */
    boolean toggleLike(Integer id);

    /** 删自己名下的那一条（顶层那一条会连名下回复一起删） */
    void delete(Integer id);
}
