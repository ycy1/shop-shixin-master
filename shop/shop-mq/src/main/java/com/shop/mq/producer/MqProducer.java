package com.shop.mq.producer;

import com.shop.mq.config.MqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// 示例发送者：在任意 Spring Bean 中注入后调用，routing key 必须与 MqConfig 的绑定键匹配
@Component
@RequiredArgsConstructor
public class MqProducer {

    private final RabbitTemplate rabbitTemplate;

    // 发到 direct 交换机：routing key 需与绑定键完全一致，未匹配的消息会被静默丢弃
    public void sendDirect(String routingKey, String message) {
        rabbitTemplate.convertAndSend(MqConfig.DIRECT_EXCHANGE, routingKey, message);
    }

    // 发到 topic 交换机：routing key 按 . 分段通配，可用 MqConfig.TOPIC_ROUTING_KEY_* 验证
    public void sendTopic(String routingKey, String message) {
        rabbitTemplate.convertAndSend(MqConfig.TOPIC_EXCHANGE, routingKey, message);
    }
}
