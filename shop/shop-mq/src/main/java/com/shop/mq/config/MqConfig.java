package com.shop.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 示例拓扑：应用启动时由 RabbitAdmin 自动声明交换机、队列和绑定
@Configuration
public class MqConfig {

    // direct 交换机：routing key 必须与绑定键完全一致，否则消息不路由
    public static final String DIRECT_EXCHANGE = "shop.demo.direct.exchange";
    public static final String DIRECT_QUEUE_1 = "shop.demo.direct.queue1";
    public static final String DIRECT_QUEUE_2 = "shop.demo.direct.queue2";
    // alpha 只会进 queue1，beta 只会进 queue2
    public static final String DIRECT_ROUTING_KEY_1 = "shop.demo.direct.alpha";
    public static final String DIRECT_ROUTING_KEY_2 = "shop.demo.direct.beta";

    // topic 交换机：routing key 按 . 分段通配
    public static final String TOPIC_EXCHANGE = "shop.demo.topic.exchange";
    public static final String TOPIC_QUEUE_1 = "shop.demo.topic.queue1";
    public static final String TOPIC_QUEUE_2 = "shop.demo.topic.queue2";
    // # 匹配零到多段，所以 queue1 能收到 shop.demo.topic.order.created
    public static final String TOPIC_ROUTING_KEY_ALL = "shop.demo.topic.#";
    // * 恰好匹配一段，所以 queue2 只收 shop.demo.topic.order.xxx
    public static final String TOPIC_ROUTING_KEY_ORDER = "shop.demo.topic.order.*";

    // 第二个参数 durable=true：broker 重启后交换机和队列仍然存在
    @Bean
    public DirectExchange demoDirectExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public Queue demoDirectQueue1() {
        return QueueBuilder.durable(DIRECT_QUEUE_1).build();
    }

    @Bean
    public Queue demoDirectQueue2() {
        return QueueBuilder.durable(DIRECT_QUEUE_2).build();
    }

    // queue1 绑定 alpha
    @Bean
    public Binding demoDirectBinding1() {
        return BindingBuilder.bind(demoDirectQueue1()).to(demoDirectExchange()).with(DIRECT_ROUTING_KEY_1);
    }

    // queue2 绑定 beta
    @Bean
    public Binding demoDirectBinding2() {
        return BindingBuilder.bind(demoDirectQueue2()).to(demoDirectExchange()).with(DIRECT_ROUTING_KEY_2);
    }

    @Bean
    public TopicExchange demoTopicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public Queue demoTopicQueue1() {
        return QueueBuilder.durable(TOPIC_QUEUE_1).build();
    }

    @Bean
    public Queue demoTopicQueue2() {
        return QueueBuilder.durable(TOPIC_QUEUE_2).build();
    }

    // queue1 绑定 shop.demo.topic.#
    @Bean
    public Binding demoTopicBinding1() {
        return BindingBuilder.bind(demoTopicQueue1()).to(demoTopicExchange()).with(TOPIC_ROUTING_KEY_ALL);
    }

    // queue2 绑定 shop.demo.topic.order.*
    @Bean
    public Binding demoTopicBinding2() {
        return BindingBuilder.bind(demoTopicQueue2()).to(demoTopicExchange()).with(TOPIC_ROUTING_KEY_ORDER);
    }
}
