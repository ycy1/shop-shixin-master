package com.shop.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.plaza.ShopPlazaQueryDto;
import com.shop.entity.ShopPlazaMessage;

/**
 * @author ycy
 * @title ShopPlazaReadService
 * @description 前台广场的读接口（Web 与 App 两端共用这一份，判据不能分叉）
 */
public interface ShopPlazaReadService {

    /**
     * 广场那一页：一次十条（条数由前端在 pageSize 上给，PageUtil 从请求串里取），默认置顶打头、按发布时间倒序；
     * orderByLike=1 时换成点赞最多的排最前（搜索结果页那一块用），置顶退成同数时的次级判据。
     *
     * 不要求登录 —— 广场是内容页，游客照样要能看；登录时每一行多带回「我赞过没有」那一格。
     * 这一份永远只看 status=1 且 audit_status=1 的那一些（待审核和已下架的不上广场，
     * 那一刀由服务层传 onlyPublic 压在 SQL 里，参数点不出来）。
     *
     * @param query 查询条件（keyword / relationType / orderByLike，都可以不传）
     * @return 消息分页，不含正文 content（列表卡片读的是 content_text 摘要）
     */
    IPage<ShopPlazaMessage> page(ShopPlazaQueryDto query);

    /**
     * 我自己发的那些（个人中心「广场消息」那一档）：按创建时间倒序，一次一页。
     *
     * 必须登录 —— 这一页的主语就是「我」，没登录连查谁都不知道。
     * 和广场那一页最大的分别是这里不拦审核状态：待审核和已拒绝的都要在这一页露出来，
     * 人要在这里看到审到哪一步、那句审核备注，然后把那一条改掉或删掉
     *（改删走的动作接口在 ShopPlazaWriteService，同一道归属闸）。
     *
     * @param query 查询条件（keyword / relationType，都可以不传）
     * @return 自己名下的消息分页，同样不含正文
     */
    IPage<ShopPlazaMessage> myPage(ShopPlazaQueryDto query);

    /**
     * 一条广场消息（详情页，带正文）。
     *
     * 已发布 + 未下架的谁都能点；没审核过的只有作者本人看得得到（要让他看到「待审核 / 已拒绝 + 那句备注」），
     * 别人点进来收到的还是同一句「这条消息不存在」—— 分档就等于让人拿 id 一句句试出这一条挂在谁名下、审到哪一步了
     *（口径同收藏、删评论那两处）。
     *
     * @param id 消息id
     * @return 消息详情，联表带回发布者昵称、关联对象名和我是否赞过
     */
    ShopPlazaMessage detail(Long id);
}
