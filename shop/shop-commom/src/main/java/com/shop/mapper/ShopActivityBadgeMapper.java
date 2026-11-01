package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivityBadge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 营销徽章定义 Mapper接口
 *
 * 读的是「徽章管理」那一页的列表和详情，外加自动判定那两条批量取数（签到签够了但还没拿到签到徽章的那些人、
 * 买够件数但还没拿到门店/消费徽章的那些人）。两条都是<b>全平台一批人</b>的，一轮定时任务一条读。
 * 发放都收在 shop-api 的那一份发放服务里（{@code ShopActivityBadgeGrantService}），
 * 这里只负责把数取回来 —— 兑现发不发，那些判据不在 SQL 里（达标线比没比过在 SQL 里比完了）。
 */
@Mapper
public interface ShopActivityBadgeMapper extends BaseMapper<ShopActivityBadge> {

    /**
     * 分页查询徽章：关键字跨名称/文字两列搜、按类型和状态筛，还要联门店/活动带出目标名称、数出已获得人数。
     * 写法同优惠券那张表（自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）。
     */
    IPage<ShopActivityBadge> selectBadgePage(IPage<ShopActivityBadge> page, @Param("q") ShopActivityBadge query);

    /**
     * 单条徽章（列表那一条 SQL 去掉筛选条件、按 id 取），和分页两条路共用同一套联表
     */
    ShopActivityBadge selectBadgeById(@Param("id") Long id);

    /**
     * 定时任务那一条批量判定：签到签够了、但还没拿到对应徽章的（人，徽章）组合，一行就是"该把这一枚发给这个人"。
     *
     * 原来这一档是挂在签到那一步同步判的（每次签到多两趟 SQL，公网 MySQL 上就是签到慢的那一截），
     * 现在搬到这里一次算完。天数就是该用户名下签到表的行数（补签同样算一天，口径和达档奖励一致）。
     *
     * 三条谓词各自挡一件事：status = 1 把禁用中的收起来；reach_count &gt; 0 把"门槛忘了填"的那几枚
     * 当成只走手工下发（门槛是 0 等于"签过一天就成群发出去"，那种误配置宁可一枚都不自动发）；
     * NOT EXISTS 把已经拿到过的剔掉 —— 一枚徽章一个人只有一行（uk_badge_user），重复判定只是白跑一遍兑现。
     * 差别只在 badge_type 钉死 3 签到、达标线在 SQL 里就比完（HAVING），所以回来的一定是够线的。
     * 一人多枚会出多行（一枚一行），所以发放那一趟不用再去数一次天数。
     */
    List<ShopActivityBadge> selectSigninBadgeHits();

    /**
     * 定时任务那一条批量判定：买够件数、但还没拿到对应的 1 门店 / 4 消费徽章的（人，徽章）组合。
     *
     * 原来这两档挂在支付回调上判（一次回调按这一单铺到的每家店各捞一趟候选、再数一次存量），
     * 2026-10-18 起和签到那一档同处理：一轮任务一条 SQL 把全平台的人一次比完。
     *
     * 件数比的是<b>存量</b>不是这一次买了多少：Σ shop_order_item.quantity，只算真付过钱的那几单
     *（sys_order.status 1已支付 / 2已完成）—— 0 待支付还没成交，3/4/5 那三档取消、退款、关闭
     * 不该继续替人撑着达标线。分几笔买满同一档同样算。
     *
     * 门店那一档和消费那一档只差"件数按不按店筛"，所以合在一条 SQL 里：联徽章时门店类拿
     * {@code t.target_id = 明细行的 store_id} 钉住那一家（A 店的徽章不会因为在 B 店买够而发出去，
     * target_id 留空的门店徽章一条也比不中），消费类不绑目标、这个人的各家件数加到一起比。
     * 一条徽章一个人一行，够线几枚出几行。
     */
    List<ShopActivityBadge> selectPurchaseBadgeHits();
}
