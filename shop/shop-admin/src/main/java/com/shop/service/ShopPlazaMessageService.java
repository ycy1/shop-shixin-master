package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.plaza.ShopPlazaAuditDto;
import com.shop.entity.ShopPlazaMessage;

import java.util.List;

/**
 * 广场消息 服务接口（后台 站点管理 → 广场消息）
 *
 * 这一份只管后台的读、审核、下架和删：广场上的内容都是前台用户发的，
 * 所以这里没有 add（发布那条写在 shop-api 的 ShopPlazaWriteService#publish，两端共用那一份判据）。
 */
public interface ShopPlazaMessageService extends IService<ShopPlazaMessage> {

    /**
     * 分页查询广场消息（待审核的那几条也在这一页上，所以不按 onlyPublic 收窄；
     * 发布者昵称和关联对象名联表带出，三个计数按明细表现算）
     *
     * @param query 查询条件（keyword / relationType / auditStatus / status / isDeleted；
     *              最后一格填 1 就只捞已删除的那些，不填按未删除 —— 这一页是唯一翻得到软删行的地方）
     * @return 消息分页，不含正文；正文由 {@link #selectPlazaById} 给详情弹窗
     */
    IPage<ShopPlazaMessage> selectPage(ShopPlazaMessage query);

    /**
     * 单条广场消息（详情弹窗，带正文）：已删除的那一条后台也读得回来，
     * 走的是 XML 里那一条而不是 getById（后者被全局逻辑删除接管，软删行一律回 null）
     *
     * @param id 消息id
     * @return 消息；读不到就报"广场消息不存在"
     */
    ShopPlazaMessage selectPlazaById(Long id);

    /**
     * 审核：判 1 已发布 或 2 已拒绝。
     *
     * 判成 1 且这一条从没上过广场时，服务端同一趟盖 publish_time 并给发布者 10 积分（补一行流水）；
     * 判成 2 只写状态和备注，不给分。
     *
     * @param form 审核参数（id + 审核结果 + 备注）
     */
    void audit(ShopPlazaAuditDto form);

    /**
     * 批量审核（列表上方那颗「批量审核」）：把选中的那几条一次判成同一档、写同一句备注。
     *
     * 每条走的判据和单条审核是同一份（送分闸门还是 publish_time 空不空），所以「通过才给分、重复点不再给」
     * 这一条不会因为换成批量就松掉；N 条通过就是 N 笔分、N 条站内通知，和逐条点 N 次一模一样。
     *
     * 整批一个事务：某一条真出错（比如积分账上不去）整批回滚，不会出现「判了一半」的广场；
     * 唯独捞不到的那条（多选以后被别人删掉了）跳过不报错 —— 为一条已经不存在的消息把其余几条的结论一起回滚，
     * 比漏判一条更糟。已删除的那一行 getById 本来就回 null，所以混进多选也只会安静跳过。
     *
     * @param form 批量审核参数（ids + 审核结果 + 备注；id 那一格这一条不看）
     */
    void auditBatch(ShopPlazaAuditDto form);

    /**
     * 修改：后台动 标题 / 正文 / 广场消息图 / 置顶 / 推荐 / 上下架 这六格（他这一轮点名的口径：后台也改得动消息内容，
     * 已删除的那一条也改得动）。正文的判据和摘要都跟前台发布那一路共用同一把尺，广场消息图那一串照表单写、不传就是清掉；
     * 发布人、关联对象和审核那几格不由这一趟动，也不把已发布的打回待审核（打回就等于重新排队、还会重复送分）
     *
     * @param message 取 id / title / content / plazaImage / isTop / isRecommend / status 七格，其余一概不认
     */
    void update(ShopPlazaMessage message);

    /**
     * 批量逻辑删除：任何审核状态都能删（违规内容等下架子都来不及，删掉最直接）。
     * 名下收到的赞和分享行不回收 —— 那是"谁在什么时候赞过/转发过"的事实留痕，口径同删评论时不动点赞行。
     *
     * @param ids 主键集合
     */
    void deleteByIds(List<Long> ids);
}
