package com.shop.api.task;

import com.shop.api.service.ShopActivityBadgeGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 徽章那两条定时任务（形状同 shop-quartz 里的 TaskQuartzFileCenter：调用目标写「bean 名.方法名()」）
 *
 * 这一类只是把 quartz 接到业务上，判据一概不在这里：够不够线、发给谁、发出去要兑现什么，
 * 全在 {@link ShopActivityBadgeGrantService} 那一份里 —— 和后台「下发」共用同一条落库路，
 * 搬进任务不能变成第二套判据。
 *
 * 为什么自动那两条都走定时（2026-10-18）：它们比的都是这个人名下的**存量** —— 签到那一档是累计天数、
 * 门店/消费那一档是累计件数，这种数按天才动一次，挂在「签一下」「付一笔」那条链上只是多背几趟公网 SQL
 *（MySQL 在那台公网机器上，一发一个来回）。现在签到和支付回调两条链上都零徽章 SQL。
 * 代价是徽章不再即时到账，最晚延迟一个任务周期；好处是每轮全平台重算一遍，
 * 以前错过的那些（付款时徽章还没配上、或者这一版上线之前的老订单）也会在下一轮补齐。
 *
 * 注册（shop/sql/add_sys_job.sql 插这两行，跑完要重启、或到「定时任务」页对这两行点一次「启动」才进调度器）：
 * {@code badgeTask.grantSigninBadges()} 和 {@code badgeTask.grantPurchaseBadges()}，
 * cron 一天一次就够（如 {@code 0 10 0 * * ?} 和 {@code 0 20 0 * * ?}，两行错开十分钟免得撞在一起），
 * 「是否并发执行」都选<b>禁止</b> —— 一轮还没跑完又来一轮只会撞 uk_badge_user 白跑一遍。
 */
@Component("badgeTask")
@RequiredArgsConstructor
public class TaskQuartzBadge {

    private final ShopActivityBadgeGrantService shopActivityBadgeGrantService;

    /** 一轮判完：把全平台"签到签够了、但还没拿到对应徽章"的那些人补齐 */
    public void grantSigninBadges() {
        shopActivityBadgeGrantService.grantSigninBadges();
    }

    /** 一轮判完：把全平台"买够件数了、但还没拿到对应的门店/消费徽章"的那些人补齐 */
    public void grantPurchaseBadges() {
        shopActivityBadgeGrantService.grantPurchaseBadges();
    }
}
