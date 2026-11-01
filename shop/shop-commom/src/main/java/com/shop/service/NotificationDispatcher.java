package com.shop.service;

import com.shop.event.NotificationEvent;

/**
 * 把一条通知事件交给「进程外」的那一段（现在是 RabbitMQ，实现见 shop-mq 的 RabbitNotificationDispatcher）。
 *
 * 为什么留一个接口，而不在 NotificationsUtil 里直接摸 RabbitTemplate：
 * shop-commom 是纯公共层、不带消息中间件的依赖；而且这个工程没有 MQ 也必须能跑 ——
 * 拿不到实现时 NotificationsUtil 退回原来那条「本地直接投递」。
 */
public interface NotificationDispatcher {

    /** 投出去就返回；真落库由消费端做。失败直接抛，调用方决定怎么兜 */
    void dispatch(NotificationEvent event);
}
