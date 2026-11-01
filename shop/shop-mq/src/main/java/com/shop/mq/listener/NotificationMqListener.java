package com.shop.mq.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.shop.event.NotificationEvent;
import com.shop.mq.config.NotificationMqConfig;
import com.shop.utils.NotificationsUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 通知消费者：把 RabbitNotificationDispatcher 投进来的 JSON 还原成事件，交给 NotificationsUtil#deliver 落库。
 *
 * 确认模式是 manual（application-mq.yml 里定的），三个分支：
 * · 成功（deliver 返回 0）→ basicAck；
 * · 失败、还没到 MAX_RETRY → basicNack(requeue=false)：消息被主队列的死信参数送去
 *   `shop.notification.dlx` → `shop.notification.retry.queue`（30 秒 TTL，死信指回主交换机），
 *   到点自动回到主队列再试一次 —— 这就是"转发回主队列、最多三次"的那条环；
 * · 失败、已经到 MAX_RETRY → basicAck 收掉：不再自动重试（那一条在库里 fail_count=3、
 *   send_status=0，管理员已经收到提醒，可以在后台点「重试」重新走这一整圈）。
 * · 反序列化失败 = 消息本身坏了，重投多少次都一样 → 打日志 + basicAck 丢掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationMqListener {

    private final NotificationsUtil notificationsUtil;

    private final ObjectMapper objectMapper;

    @RabbitListener(queues = NotificationMqConfig.NOTIFICATION_QUEUE)
    public void onNotification(String payload, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        NotificationEvent event;
        try {
            event = objectMapper.readValue(payload, NotificationEvent.class);
        } catch (Exception e) {
            log.error("通知消息反序列化失败，丢弃这一条：{}", payload, e);
            channel.basicAck(deliveryTag, false);
            return;
        }
        int fails = notificationsUtil.deliver(event);
        if (fails == 0 || fails >= NotificationsUtil.MAX_RETRY) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        log.warn("通知第 {} 次发送失败，交给重试队列：sendCode={}, title={}",
                fails, event.getSendCode(), event.getTitle());
        channel.basicNack(deliveryTag, false, false);
    }
}
