package com.o2o.carpooling.order;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OrderRabbitConfiguration {

    @Bean
    DirectExchange orderTimeoutDelayExchange(OrderMessagingProperties properties) {
        return new DirectExchange(properties.getTimeout().getDelayExchange(), true, false);
    }

    @Bean
    DirectExchange orderTimeoutExpiredExchange(OrderMessagingProperties properties) {
        return new DirectExchange(properties.getTimeout().getExpiredExchange(), true, false);
    }

    @Bean
    Queue orderTimeoutDelayQueue(OrderMessagingProperties properties) {
        return QueueBuilder.durable(properties.getTimeout().getDelayQueue())
            .withArgument("x-dead-letter-exchange", properties.getTimeout().getExpiredExchange())
            .withArgument("x-dead-letter-routing-key", properties.getTimeout().getExpiredRoutingKey())
            .build();
    }

    @Bean
    Queue orderTimeoutExpiredQueue(OrderMessagingProperties properties) {
        return QueueBuilder.durable(properties.getTimeout().getExpiredQueue()).build();
    }

    @Bean
    Binding orderTimeoutDelayBinding(Queue orderTimeoutDelayQueue, DirectExchange orderTimeoutDelayExchange, OrderMessagingProperties properties) {
        return BindingBuilder.bind(orderTimeoutDelayQueue)
            .to(orderTimeoutDelayExchange)
            .with(properties.getTimeout().getDelayRoutingKey());
    }

    @Bean
    Binding orderTimeoutExpiredBinding(Queue orderTimeoutExpiredQueue, DirectExchange orderTimeoutExpiredExchange, OrderMessagingProperties properties) {
        return BindingBuilder.bind(orderTimeoutExpiredQueue)
            .to(orderTimeoutExpiredExchange)
            .with(properties.getTimeout().getExpiredRoutingKey());
    }
}
