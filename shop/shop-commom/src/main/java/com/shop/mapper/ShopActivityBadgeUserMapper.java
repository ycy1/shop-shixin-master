package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivityBadgeUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户徽章 Mapper接口
 *
 * 写入有四条路：发放那一条（shop-api 的 {@code ShopActivityBadgeGrantService#grant}，插行）、
 * 后台「获取记录」弹窗里的删除一条（本接口最后那条 &lt;delete&gt;，物理删掉一行），
 * 加上改「是否展示」两条都走 BaseMapper 的 updateById、只交 id 和 is_show 两格
 *（后台同一弹窗里那颗开关、前台「个人信息 → 徽章」每行那颗「展示 / 收起」——
 *  后者在 {@code ShopBadgeWriteService}，归属无条件钉登录 id）。
 * 插之前一定先查过"这个人手上有没有这一枚" —— 唯一键 uk_badge_user 是最后那道闸。
 * 读是列表页那颗「获取记录」弹窗的明细，另加一条"这一枚现在都在谁手上"给那颗「下发」判重用。
 * 名称和形状读本表自己的快照，不联定义表 —— 联了就会把徽章后来改过的名字显示到已经拿到的人身上。
 */
@Mapper
public interface ShopActivityBadgeUserMapper extends BaseMapper<ShopActivityBadgeUser> {

    /**
     * 分页查某一枚徽章的获取记录（按来源和状态可选筛，最新一条在前）
     */
    IPage<ShopActivityBadgeUser> selectRecordPage(IPage<ShopActivityBadgeUser> page,
                                                  @Param("q") ShopActivityBadgeUser query);

    /**
     * 这一枚徽章现在都在谁手上（那颗「下发」一次给一批人，先一次把已经有的人捞出来，
     * 他们直接跳过 —— 一个徽章一个人只有一行，重复落就是撞 uk_badge_user，重复兑现更是白头发一笔积分）。
     *
     * 只 select user_id 这一列。自定义 SQL 不走全局逻辑删除，所以 is_deleted = 0 在 XML 里手写。
     */
    List<Long> selectUserIdsByBadge(@Param("badgeId") Long badgeId);

    /**
     * 按 id 物理删除获取记录（后台「获取记录」弹窗里那颗「删除」/「批量删除」）。
     *
     * 为什么不走别的 shop_* 表那套 is_deleted = 1：uk_badge_user (badge_id, user_id) 不含 is_deleted，
     * 软删留下的那一行还占着这两个格，这一枚对他就永久发不出去了（自动判定那条按 is_deleted = 0 判"还没拿到"，
     * 重新列进候选后 insert 撞键被静默吞掉；「下发」那一路报的是"都已经持有"这种假话）。
     * 判据和口径写在 XML 那条注释和 service 里。
     *
     * @param ids 要删掉的明细行 id（服务层已去过空值、去过重，消费者账号那一头还验过这些行都在他名下）
     * @return 实际删掉的行数（id 不存在就是 0，不报错）
     */
    int deletePhysicallyByIds(@Param("ids") List<Long> ids);
}
