package com.shop.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 站内通知那一条业务拓扑（和 shop.demo.* 那套练习拓扑分开，别混用）。
 *
 * 三条队列走成一个环，失败最多自动重试 MAX_RETRY 次：
 * <pre>
 *   业务提交后 → shop.notification.queue ──消费失败 nack──→ shop.notification.dlx
 *                      ↑                                        ↓
 *                      └──── 30 秒后（TTL 到期，死信指回主交换机）── shop.notification.retry.queue
 * </pre>
 * · 主队列挂了死信参数，拒收的消息不会丢，而是先进死信交换机；
 * · 死信交换机路由到「重试队列」；重试队列不挂消费者，只做延时：消息在里头躺 30 秒（x-message-ttl），
 *   到期被自己的死信参数送回主交换机 + 原路由键，于是又回到主队列 —— RabbitMQ 没有原生延时队列，这就是标准绕法；
 * · 次数记在 sys_notifications.fail_count（消费者每次失败 +1），满 3 次消费者直接 ack 停手、通知管理员，
 *   那一条留在库里等后台手动重试。
 *
 * ⚠️ 队列参数声明后改不了：主队列要是早先已经建过（没有这两格死信参数），启动会报 PRECONDITION_FAILED，
 * 先在管理台把 shop.notification.queue 删掉再启动。
 *
 * 以后要加「推送 / 短信 / 统计」，各自绑一条 queue 到同一个 routing key 就行，生产方不用改。
 */
@Configuration
public class NotificationMqConfig {

    public static final String NOTIFICATION_EXCHANGE = "shop.notification.exchange";
    public static final String NOTIFICATION_QUEUE = "shop.notification.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "shop.notification.created";

    /** 死信交换机：主队列拒收的消息先到这里，再路由进重试队列 */
    public static final String NOTIFICATION_DLX = "shop.notification.dlx";
    /** 重试队列（就是那条"死信队列"）：不挂消费者，躺够 TTL 自己回主队列 */
    public static final String NOTIFICATION_RETRY_QUEUE = "shop.notification.retry.queue";
    public static final String NOTIFICATION_RETRY_ROUTING_KEY = "shop.notification.retry";

    /** 两次重试之间隔多久（毫秒）：改这一格等于调整退避节奏 */
    public static final int NOTIFICATION_RETRY_TTL_MS = 30_000;

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange notificationDlx() {
        return new DirectExchange(NOTIFICATION_DLX, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        // durable：broker 重启后这条队列还在（投递那侧也发持久化消息，见 RabbitNotificationDispatcher）
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                // 消费失败 nack(requeue=false) 的消息：先进死信交换机，再进重试队列
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_RETRY_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue notificationRetryQueue() {
        // 不挂消费者：消息在这里躺 TTL 毫秒，到期按死信参数自己回到主交换机 → 主队列
        return QueueBuilder.durable(NOTIFICATION_RETRY_QUEUE)
                .withArgument("x-message-ttl", NOTIFICATION_RETRY_TTL_MS)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding notificationDlxBinding() {
        return BindingBuilder.bind(notificationRetryQueue()).to(notificationDlx()).with(NOTIFICATION_RETRY_ROUTING_KEY);
    }
}
