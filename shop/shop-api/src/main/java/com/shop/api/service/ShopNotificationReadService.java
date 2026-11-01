package com.shop.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.vo.notifications.NotificationUnreadVo;
import com.shop.vo.notifications.NotificationsListVo;

/**
 * 我的消息 读接口（Web 消息中心、App 消息页、后台铃铛三端共用这一份）
 *
 * 为什么放 shop-api 而不是复用后台那份 SysNotificationService：pom 里是 shop-admin → shop-api，
 * 反向编译不到，而三端要的又都是「只看自己名下那几条」这套判据 —— 判据只能有一份，
 * 所以这一份落在下面那一层（后台自己的增删改查仍然用它那一份，两条读路各管各的可见性）。
 *
 * 可见性在发送那一刻就定死了（接收人铺进 sys_notifications_receiver，全员广播的认主表 is_broadcast），
 * 这里只按「我名下有没有这一行」比，不再去解析 notice_push 那串 JSON。
 */
public interface ShopNotificationReadService {

    /** 当前用户可见的消息分页（未读的打头，按时间倒序），category 只收这一档、null 两档一起 */
    IPage<NotificationsListVo> page(Integer category);

    /** 未读汇总：总数 + 系统/互动两档各多少（红点看 total，两档 tab 各看自己那一格） */
    NotificationUnreadVo unread();

    /** 点进某一条就算已读（只对自己那行生效，消息不存在就报） */
    void read(Long id);

    /** 当前这一档全部已读，category 为空表示两档一起清 */
    void readAll(Integer category);

    /** 删掉自己名下的一条（只是对自己隐藏，别人照样看得见） */
    void delete(Long id);

    /** 清掉自己名下的全部（同 delete，只对自己隐藏），category 为空表示两档一起清 */
    void deleteAll(Integer category);
}
