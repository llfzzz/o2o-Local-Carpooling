package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.OrderDetail;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Optional;

@FeignClient(name = "order-service")
interface OrderFeignClient {
    @GetMapping("/api/orders/{orderId}")
    OrderDetail get(@PathVariable("orderId") String orderId);

    @PostMapping("/api/orders/{orderId}/pay")
    OrderDetail markPaid(@PathVariable("orderId") String orderId);
}

@Component
class FeignOrderClient implements OrderClient {

    private final OrderFeignClient orderFeignClient;

    FeignOrderClient(OrderFeignClient orderFeignClient) {
        this.orderFeignClient = orderFeignClient;
    }

    @Override
    public Optional<OrderDetail> findOrder(String orderId) {
        try {
            return Optional.of(orderFeignClient.get(orderId));
        } catch (FeignException.NotFound exception) {
            return Optional.empty();
        }
    }

    @Override
    public OrderDetail markPaid(String orderId) {
        try {
            return orderFeignClient.markPaid(orderId);
        } catch (FeignException.Conflict conflict) {
            // Order-side state machine refused the payment (e.g. already timeout-cancelled).
            // Translate to a domain signal so the callback pipeline can decide, without leaking feign.
            throw new OrderPayConflictException(orderId, conflict);
        }
    }
}
