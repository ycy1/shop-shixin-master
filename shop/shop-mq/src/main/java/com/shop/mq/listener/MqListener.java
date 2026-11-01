package com.shop.mq.listener;

import com.rabbitmq.client.Channel;
import com.shop.mq.config.MqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 示例消费者：manual 确认模式（见 application-mq.yml），每条消息处理完必须 ack/nack，否则会一直处于 unacked
@Slf4j
@Component
public class MqListener {

    // queue1 只接收 routing key = shop.demo.direct.alpha 的消息
    @RabbitListener(queues = MqConfig.DIRECT_QUEUE_1)
    public void onDirectQueue1(String message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle("direct queue1", message, channel, deliveryTag);
    }

    // queue2 只接收 routing key = shop.demo.direct.beta 的消息
    @RabbitListener(queues = MqConfig.DIRECT_QUEUE_2)
    public void onDirectQueue2(String message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle("direct queue2", message, channel, deliveryTag);
    }

    // queue1 绑定 shop.demo.topic.#，shop.demo.topic. 开头的消息都会进来
    @RabbitListener(queues = MqConfig.TOPIC_QUEUE_1)
    public void onTopicQueue1(String message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle("topic queue1", message, channel, deliveryTag);
    }

    // queue2 绑定 shop.demo.topic.order.*，只接收 shop.demo.topic.order.xxx
    @RabbitListener(queues = MqConfig.TOPIC_QUEUE_2)
    public void onTopicQueue2(String message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle("topic queue2", message, channel, deliveryTag);
    }

    // deliveryTag 是当前 channel 内的投递编号，ack/nack 时必须原样传回
    private void handle(String queue, String message, Channel channel, long deliveryTag) throws IOException {
        try {
            log.info("{} 收到消息: {}", queue, message);
            // 处理成功：确认消息，broker 会删除它
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("{} 处理失败，消息重新入队", queue, e);
            // 处理失败：第二个参数 false 只拒绝当前消息，第三个参数 true 表示重新入队
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
