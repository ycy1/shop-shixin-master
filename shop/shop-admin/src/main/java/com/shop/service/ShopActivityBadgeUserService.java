package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopActivityBadgeUser;

import java.util.List;

/**
 * 用户徽章 服务接口（后台这一页：读明细 + 删记录 + 改状态 + 改展示）
 *
 * 三条写入路（支付回调判定、签到判定、列表上那颗「下发」）同住 shop-api 的
 * {@code ShopActivityBadgeGrantService}，所以这里不开 add/update ——
 * 手工往这张表插一行等于凭空给一个人发一枚徽章，还顺带把徽章上那笔积分和那几张券兑现了。
 *
 * 开在这一头的三条都是往回收的动作，不动账户、不动券，风险比发一枚小得多：
 * {@link #deleteByIds} 抹掉一行，{@link #updateStatus} 只改 status 那一列，
 * {@link #updateIsShow} 只改 is_show 那一列。
 */
public interface ShopActivityBadgeUserService extends IService<ShopActivityBadgeUser> {

    /**
     * 分页查某一枚徽章的获取记录（列表页那颗「获取记录」弹窗）
     *
     * @param query 查询条件（badgeId/source/status/userId/keyword）
     * @return 用户徽章分页，昵称和手机号已联表带出
     */
    IPage<ShopActivityBadgeUser> selectRecordPage(ShopActivityBadgeUser query);

    /**
     * 删除获取记录：把一枚徽章从某个人名下删掉（弹窗里那颗「删除」和「批量删除」，一行就是"一人一枚"）
     *
     * 口径两条，都定在他选的那一档上：
     * ① 只删这一张表上的这一行，当初随徽章一起兑现的那笔积分和那几张券**不收回**
     *    （分和券各有自己的账和流水，回到徽章这一格来冲账就成两处账了；要收分走积分页那颗「修改」，
     *     要收券走优惠券页的作废，那两条本来就记得出是谁动的）；
     * ② 物理删除，不是别处的 is_deleted = 1 —— uk_badge_user (badge_id, user_id) 不含软删那一格，
     *    留一行就等于把这个人这一枚永久封住（详见 Mapper 接口那条说明）。
     *
     * @param ids 要删掉的明细行 id
     */
    void deleteByIds(List<Long> ids);

    /**
     * 改一条获取记录的「状态」：只动 status 这一列（弹窗里那一格那颗开关，一改就提交）
     *（这一格说的是「这一枚还算不算数」，不是账 —— 失效了也不收回当初兑现的积分和券，
     *  更不抹掉这一行；跟「是否展示」那一条各管各的：状态管认不认，展示管挂不挂在账号卡下面）。
     *
     * @param id     明细行 id（一行就是"一人一枚"）
     * @param status 1 有效、0 失效，两档之外一律拒（2 已过期 那一档已下线，过期由前端拿 end_time 现比）
     */
    void updateStatus(Long id, Integer status);

    /**
     * 改一条获取记录的「是否展示」：只动 is_show 这一列，名称那三格快照、有效期、状态都不碰
     *（这一格是给人看的开关，不是账 —— 所以既不收回奖励，也不影响"这一枚还在不在他手上"）。
     *
     * @param id     明细行 id（一行就是"一人一枚"）
     * @param isShow 1 展示、0 隐藏，两档之外一律拒
     */
    void updateIsShow(Long id, Integer isShow);
}
