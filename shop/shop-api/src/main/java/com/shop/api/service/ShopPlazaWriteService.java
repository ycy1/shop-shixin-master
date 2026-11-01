package com.shop.api.service;

import com.shop.dto.plaza.ShopPlazaPublishDto;
import com.shop.dto.plaza.ShopPlazaShareDto;
import com.shop.dto.plaza.ShopPlazaUpdateDto;
import com.shop.entity.ShopPlazaMessage;

/**
 * @author ycy
 * @title ShopPlazaWriteService
 * @description 前台广场的写动作（发布、改自己那条、删自己那条、点赞开关、记一次分享），两端共用 /api/comm 那一条路径
 */
public interface ShopPlazaWriteService {

    /**
     * 发布一条广场消息：落 shop_plaza_message 一行，一律 0 待审核，此刻不进广场、也还不给分。
     *
     * 送那 10 分是在后台「审核通过」那一刻（ShopPlazaMessageService#audit，同一个事务里补流水），
     * 所以这一颗写完只是把话放出来 —— 判据、给分口径都在实现类的类注释里。
     *
     * @param form 发布参数（正文必填；关联对象那一档填了就查得到才收）
     * @return 刚落库的那一行（昵称、头像不在这一行里，前端发完回读一次列表）
     */
    ShopPlazaMessage publish(ShopPlazaPublishDto form);

    /**
     * 改自己发的那一条：正文/标题/广场消息图/关联对象都能换，改完一律打回 0 待审核，重走一遍审核那一路。
     *
     * publish_time 不动（后台 audit 里靠它判「是不是头一次通过」，清掉它二次通过会再送一遍 10 分），
     * status 也不动（下架那条理由不该被改个正文自己翻回上架）。
     *
     * @param form 修改参数（id 必填，其余五格同发布）
     */
    void update(ShopPlazaUpdateDto form);

    /**
     * 删自己发的那一条：软删这一行，名下的赞/评/分享行不回收，已经收到的那 10 分也不收回。
     *
     * @param id 消息id（不是自己的、已经删了的、根本没这个 id 三种回同一句「这条消息不存在或已经被删除」）
     */
    void delete(Long id);

    /**
     * 点赞那颗按钮：一颗管两个方向，点过就取消（硬删那一行）、没点过就点上，回按完之后的状态。
     *
     * @param id 消息id
     * @return true 已点赞，false 已取消
     */
    boolean toggleLike(Long id);

    /**
     * 记一次分享：往 shop_plaza_share 插一行留痕，只插不改不删、也不判重复。
     * 广场上的「分享数」由那张表现算，所以这一颗不动 shop_plaza_message 上的任何一列。
     *
     * @param form 分享参数（messageId 必填，渠道不传按 5 复制链接）
     */
    void share(ShopPlazaShareDto form);
}
