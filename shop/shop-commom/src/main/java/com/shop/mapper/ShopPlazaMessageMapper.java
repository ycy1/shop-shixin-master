package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopPlazaMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 广场消息 Mapper接口
 */
@Mapper
public interface ShopPlazaMessageMapper extends BaseMapper<ShopPlazaMessage> {

    /**
     * 分页查询广场消息：关键字要跨标题/摘要/发布者昵称三列搜、关联对象名要从四张表里挑一格，
     * 三个计数还得现算，条件写在 XML 里（代价是自定义 SQL 不走全局逻辑删除，is_deleted 那一刀在 XML 里手写）。
     *
     * userId = 当前登录账号 id：额外联一次点赞表把「我赞过没有」带回来（selfLiked，前台那颗按钮判可见用），
     * 游客和后台那一页传 null —— 那一层联表和那一列整个不拼，前端读不到 selfLiked 就当没赞过。
     *
     * onlyPublic = true 时把「已发布 + 正常」那一刀钉死（前台两条列表走这一档，审核没过的不该出现在广场上），
     * false 时按 q 上带的 auditStatus / status 筛（后台列表要看待审核的那些）。
     *
     * 软删行看不看得到由 q.isDeleted 这一点决定（后台那一格「删除情况」）：不填或填 0 只捞未删除，
     * 填 1 只捞已删除。前台那一路的查询条件是服务层照 DTO 一格一格搭的，DTO 里没有 isDeleted，
     * 所以前端永远只会落到「未删除」那一档。
     *
     * orderByLike = true 把序换成「点赞最多的排最前」（搜索结果页那一块用），
     * 置顶/发布时间/id 退成同数时的次序；false 就是广场那一页原本的流。
     * 认的是现算出来的那个 likeCount，不是库里那列 —— 库里从不回写。
     *
     * orderByAuditTier = true 换成后台那一屏的序：先按状态分档（待审核 → 已发布 → 已拒绝 → 已删除/已隐藏沉底），
     * 档内再按置顶、点赞、时间（只有已发布那一档有赞可比，其余档那一格现算出来是 0，等于直接落时间序）。
     * 只有后台广场管理页点这一格；前台两条列表和「我发的」那一页都是 false，流不受影响。
     * 两者同时点时这一档说话 —— 后台不按点赞排，改判靠的是「待审核全在最前面」。
     */
    IPage<ShopPlazaMessage> selectPlazaPage(IPage<ShopPlazaMessage> page,
                                           @Param("q") ShopPlazaMessage query,
                                           @Param("userId") Long userId,
                                           @Param("onlyPublic") boolean onlyPublic,
                                           @Param("orderByLike") boolean orderByLike,
                                           @Param("orderByAuditTier") boolean orderByAuditTier);

    /**
     * 「我发的」那一页（个人中心「广场消息」那一档）：同一套联表和现算口径，但两处不一样 ——
     * 归属钉死在 userId 上（所以登录是前提，不传就没有这一页），上下架不拦、审核状态由 q.auditStatus 自己点
     *（待审和已拒绝的正是这一页要给人看到、要给人改给人删的那些），序按创建时间倒序而非发布时间
     *（没通过审核的行 publish_time 是空的，按发布时间排会把刚发出去的那一条埋到列表底下去）。
     */
    IPage<ShopPlazaMessage> selectMyPlazaPage(IPage<ShopPlazaMessage> page,
                                             @Param("q") ShopPlazaMessage query,
                                             @Param("userId") Long userId);

    /**
     * 单条（带正文 content，列表那条不取正文）：和列表共用同一套联表与现算口径，
     * 审核状态不在这里拦 —— 待审核的那条发布者自己也要能看，判据在服务层。
     *
     * includeDeleted = false 是前台那一路（软删了一律回「不存在」）；true 只给后台 ——
     * 后台那一屏要翻已删除的消息，也要改它（他点名的口径），所以这一条读法要能把软删行捞回来。
     */
    ShopPlazaMessage selectPlazaById(@Param("id") Long id,
                                    @Param("userId") Long userId,
                                    @Param("includeDeleted") boolean includeDeleted);

    /**
     * 后台专用的一条 UPDATE：只按 id 认，不带 is_deleted = 0 那一刀，也不走 MyBatis-Plus 的字段策略。
     *
     * 为什么不用 updateById：全局逻辑删除（application.yml 的 logic-delete-field）会给 getById/updateById
     * 都补上 is_deleted = 0，软删的那一条既读不到也改不动 —— 而后台这一屏要的正是在已删除的行上改标题和正文。
     * 代价是留痕那一格要自己写：自定义 SQL 不触发 MyMetaObjectHandler，所以 update_by 由服务层递进来、
     * update_time 用 NOW()（写法同足迹/收藏那条 revive 的 UPDATE）。
     *
     * 只列后台改得动的那几格：publish_time / audit_status / user_id 一个都不碰 ——
     * 前者是审核「头一次通过才给分」的闸门，清掉它就等于改一次正文多领 10 分；
     * 后者是发布者那一格，实体上钉着 NEVER，后台改了等于替别人发言。
     */
    int updateContentByAdmin(@Param("e") ShopPlazaMessage message, @Param("updateBy") Long updateBy);
}
