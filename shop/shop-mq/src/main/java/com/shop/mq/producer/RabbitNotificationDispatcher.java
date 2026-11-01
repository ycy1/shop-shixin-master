package com.shop.mq.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.event.NotificationEvent;
import com.shop.mq.config.NotificationMqConfig;
import com.shop.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知事件的 RabbitMQ 投递端（NotificationsUtil 只认 NotificationDispatcher 那个接口，实现放在这一层）。
 *
 * 为什么发 JSON 字符串、不发 Java 序列化对象：消息在管理页/日志里直接能看懂，也不要求事件对象实现 Serializable；
 * 将来给事件加字段时，旧消息照样能按缺省值反序列化回来。
 * 序列化用容器里那份 ObjectMapper（和 Json 接口同一套配置）：枚举按名字走，实测 hutool 的 toBean 会把枚举认成第一个常量，
 * 别用 hutool 那套。
 * 投递用持久化消息（PERSISTENT），配 NotificationMqConfig 那条 durable 队列 —— broker 重启不丢。
 *
 * 抛异常是故意的：投不出去要让 NotificationsUtil 知道，它好在本地把这条补发掉（见那边的 onNotification）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitNotificationDispatcher implements NotificationDispatcher {

    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper;

    @Override
    public void dispatch(NotificationEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("通知事件序列化失败", e);
        }
        rabbitTemplate.convertAndSend(
                NotificationMqConfig.NOTIFICATION_EXCHANGE,
                NotificationMqConfig.NOTIFICATION_ROUTING_KEY,
                payload,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });
        log.info("通知已投递到 MQ：businessType={}, businessId={}, title={}",
                event.getType(), event.getBusinessId(), event.getTitle());
    }
}
