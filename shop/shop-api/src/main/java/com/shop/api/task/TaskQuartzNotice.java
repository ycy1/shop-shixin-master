package com.shop.api.task;

import com.shop.api.service.ShopNoticeScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 消息通知那三条到点提醒的定时任务（形状同 {@link TaskQuartzBadge}：调用目标写「bean 名.方法名()」）
 *
 * 这一类只是把 quartz 接到业务上，判据一概不在这里：哪些活动/培训算"到点了"、发给谁、发过没有，
 * 全在 {@link ShopNoticeScanService} 那一份里。
 *
 * 为什么这两类挂定时而不是挂业务动作：活动开始和培训开始都没有"某人此刻按了按钮"这个时刻 ——
 * 时间是后台配在投放开始时间 / 培训开始时间那两格上的，到点没人登录就没有挂载点。
 *
 * 注册（活动/培训那两行在 shop/sql/add_sys_job.sql，IP授权到期那一行在 shop/sql/add_trendy_ip.sql 末尾；
 * 跑完要重启、或到「定时任务」页对新插的这行点一次「启动」才进调度器）：
 * {@code noticeTask.scanActivityStart()} 和 {@code noticeTask.scanTrainingStart()}、{@code noticeTask.scanLicenseExpire()}，
 * cron 一天一次就够（{@code 0 30 8 * * ?}、{@code 0 40 8 * * ?}、{@code 0 30 9 * * ?}，三行各错开十分钟），
 * 「是否并发执行」都选<b>禁止</b> —— 两轮同时跑会把同一条活动广播两遍（去重那一趟查的是已落库的消息行，
 * 两轮都在落库前查就都判成"没发过"）。
 */
@Component("noticeTask")
@RequiredArgsConstructor
public class TaskQuartzNotice {

    private final ShopNoticeScanService shopNoticeScanService;

    /** 一轮扫完：把今天到点、还没提醒过的活动给全员广播一条 */
    public void scanActivityStart() {
        shopNoticeScanService.scanActivityStart();
    }

    /** 一轮扫完：把今天开课、还没提醒过的培训发给报名成员和讲师 */
    public void scanTrainingStart() {
        shopNoticeScanService.scanTrainingStart();
    }

    /** 一轮扫完：把到期日就在一个月后、这个到期日还没提醒过的IP授权发给它的版权人/授权人 */
    public void scanLicenseExpire() {
        shopNoticeScanService.scanLicenseExpire();
    }
}
