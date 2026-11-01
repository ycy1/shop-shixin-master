package com.shop.api.service;

/**
 * 消息通知的三条定时扫描（活动开始、培训开始、IP授权临到期）
 *
 * 这几类提醒不属于任何一个用户动作：活动是后台配好投放时间就自己到点，培训是到点就该开课，
 * 授权是合同上写死的那个日期，都没有"谁此刻按了按钮"可以挂 —— 所以由定时任务一轮一遍地比出来。
 * 形状和 {@link ShopActivityBadgeGrantService} 那两条自动发放一样：判据在服务里，任务类只做委托。
 */
public interface ShopNoticeScanService {

    /** 一轮扫到点的活动：给全员广播一条「活动已开始」（只发一次，发过就跳过） */
    void scanActivityStart();

    /** 一轮扫到点的培训：给报名成员和讲师各一条「你报名的培训开始了」（同上一条去重判据） */
    void scanTrainingStart();

    /** 一轮扫临到期的IP授权：给版权人/授权人一条「授权即将到期」（同一条授权按到期日各发一次） */
    void scanLicenseExpire();
}
