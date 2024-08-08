package com.augus.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class MyMessageConsumer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    // 指定程序监听的消息队列，
    @SneakyThrows
    @RabbitListener(queues = {"code_queue"}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        System.out.println("receiveMessage message =  " + message);
        channel.basicAck(deliveryTag, false);
    }
}
