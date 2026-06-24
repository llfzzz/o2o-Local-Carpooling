package com.o2o.carpooling.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
class OrderOutboxPublisher {

    private final OrderOutboxRepository repository;
    private final RabbitOperations rabbitOperations;
    private final ObjectMapper objectMapper;
    private final OrderMessagingProperties properties;

    OrderOutboxPublisher(
        OrderOutboxRepository repository,
        RabbitOperations rabbitOperations,
        ObjectMapper objectMapper,
        OrderMessagingProperties properties
    ) {
        this.repository = repository;
        this.rabbitOperations = rabbitOperations;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${orders.messaging.publish-fixed-delay:PT10S}")
    void publishDue() {
        publishDue(Instant.now());
    }

    int publishDue(Instant now) {
        int published = 0;
        for (OrderOutboxEvent event : repository.findPublishable(now, properties.getPublishBatchSize())) {
            try {
                publish(event, now);
                repository.markPublished(event.eventId(), now);
                published++;
            } catch (Exception exception) {
                repository.markFailed(event.eventId(), exception.getMessage(), now.plus(properties.getRetryDelay()), now);
            }
        }
        return published;
    }

    private void publish(OrderOutboxEvent event, Instant now) throws Exception {
        OrderPaymentTimeoutMessage payload = objectMapper.readValue(event.payloadJson(), OrderPaymentTimeoutMessage.class);
        String delayMillis = String.valueOf(Math.max(0, Duration.between(now, payload.paymentDeadlineAt()).toMillis()));
        rabbitOperations.convertAndSend(
            properties.getTimeout().getDelayExchange(),
            properties.getTimeout().getDelayRoutingKey(),
            event.payloadJson(),
            message -> {
                MessageProperties messageProperties = message.getMessageProperties();
                messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                messageProperties.setExpiration(delayMillis);
                return message;
            }
        );
    }
}
