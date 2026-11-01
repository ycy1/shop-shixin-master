package com.shop.api.task;

import com.shop.api.service.ShopExpireScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 过期扫描那两条定时任务（形状同 {@link TaskQuartzBadge}：调用目标写「bean 名.方法名()」）
 *
 * 这一类只是把 quartz 接到业务上，判据一概不在这里：哪一批算过期、状态怎么推，
 * 全在 {@link ShopExpireScanService} 那一份里（两条各是一条条件 UPDATE，前态压在 WHERE 里）。
 *
 * 为什么走定时而不是只用现判：用券/核销那两处的现判都还留着（任务按天跑，刚过期那几个小时内库里还是 0，
 * 不能放一张过期的券去抵钱、放一张过期的票进园），但账面得有人每天摊一次 ——
 * 否则「用户票种」「优惠券发放记录」两本列表上会一直躺着一堆看起来还能用的 0 未使用，
 * 运营要挑"哪些该退款"只能自己按日期筛。过期票是可以退的，所以这一档落下来之后退款那颗还给点。
 *
 * 注册（shop/sql/add_trendy_park.sql 插这两行，跑完要重启、或到「定时任务」页对这两行各点一次「启动」才进调度器）：
 * {@code expireTask.expireParkTickets()}（{@code 0 0 1 * * ?}）
 * 和 {@code expireTask.expireUserCoupons()}（{@code 0 10 1 * * ?}，两行错开十分钟），
 * 「是否并发执行」都选<b>禁止</b> —— 一条 UPDATE 跑两遍不会改坏数据，但也没必要让它俩同时抢锁。
 */
@Component("expireTask")
@RequiredArgsConstructor
public class TaskQuartzExpire {

    private final ShopExpireScanService shopExpireScanService;

    /** 一轮扫完：把游玩日期已经过了、还从来没核销过的公园票落成 2 已过期 */
    public void expireParkTickets() {
        shopExpireScanService.expireParkTickets();
    }

    /** 一轮扫完：把过期时间已经过了、还没用也没作废的优惠券落成 2 已过期 */
    public void expireUserCoupons() {
        shopExpireScanService.expireUserCoupons();
    }
}
