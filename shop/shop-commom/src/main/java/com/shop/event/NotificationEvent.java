package com.shop.event;

import com.shop.enums.NotificationTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Singular;

import java.util.List;

/**
 * 一条要发出去的消息通知：挂载点只负责把「谁、因为哪一行数据、说哪两句话」摆成这个对象丢给 NotificationsUtil.publish，
 * 落库、展开接收人、发邮件都在消费那一段里做。
 *
 * 为什么走事件不走直接调用：评论落库、订单签收这些动作各自有事务，通知发在事务里 ⇒ 一旦回滚，页面上评论没了、
 * 被回复那人却收到一条提醒；更要紧的是这一趟要写好几张表、还要按部门/角色展开收件人，
 * 绝不能挂在买家下单、发评论那条链上把他等的时间拉长。
 *
 * 这一份现在还会被序列化成 JSON 丢进 MQ（见 shop-mq 的 RabbitNotificationDispatcher / NotificationMqListener），
 * 所以字段不能是 final、要留无参构造 + setter —— 消费端按同一份形状反序列化回来。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /** 业务来源，同时决定 category（系统消息 / 互动消息） */
    private NotificationTypeEnum type;

    /** 那一条业务数据的 id（活动 id、订单 id、评论 id…）；定时扫描靠「同类型同 id 发过没有」去重 */
    private Long businessId;

    private String title;

    private String message;

    /** 前台点这条消息跳哪儿，可空 */
    private String link;

    /** 谁触发的（评论人、点赞人、发公告的人），可空 */
    private Long fromUserId;

    /**
     * 这一条通知在库里的身份（32 位 uuid）：publish 时钉一次，之后重试、幂等重投都靠它找回同一行
     *（消费端先按它查 sys_notifications.send_code，查到就复用/更新，不再插第二行）
     */
    private String sendCode;

    /** 定向接收人：三格全空 ⇒ 全员广播（不落接收人行，靠主表 is_broadcast 认） */
    @Singular
    private List<Long> userIds;

    /** 按部门展开：发送时把 sys_dept_user 里这几个部门的人铺成接收人行（只有公告用得上） */
    @Singular
    private List<Long> deptIds;

    /** 按角色展开：同上，走 sys_user_role */
    @Singular
    private List<Long> roleIds;
}
