package com.o2o.carpooling.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class OrderPaymentTimeoutConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    OrderPaymentTimeoutConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${orders.messaging.timeout.expired-queue:o2o.order.timeout.expired.queue}")
    void consume(String payloadJson) throws Exception {
        OrderPaymentTimeoutMessage message = objectMapper.readValue(payloadJson, OrderPaymentTimeoutMessage.class);
        orderService.expireIfPaymentPending(message.orderId());
    }
}
