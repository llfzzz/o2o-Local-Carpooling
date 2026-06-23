package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.domain.OrderSnapshot;
import com.o2o.carpooling.common.domain.OrderStateMachine;
import com.o2o.carpooling.common.domain.OrderStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final Map<String, OrderDetail> orders = new ConcurrentHashMap<>();
    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @PostMapping
    OrderDetail create(@RequestBody CreateOrderRequest request) {
        OrderDetail order = new OrderDetail(
            "order-" + UUID.randomUUID(),
            request.tripId(),
            request.riderId(),
            request.seats(),
            new Money(request.amount(), "CNY"),
            OrderStatus.PENDING_PAYMENT,
            Instant.now()
        );
        orders.put(order.orderId(), order);
        return order;
    }

    @PostMapping("/{orderId}/pay")
    OrderDetail pay(@PathVariable String orderId) {
        OrderDetail current = requireOrder(orderId);
        OrderSnapshot paid = stateMachine.pay(new OrderSnapshot(current.orderId(), current.status()));
        return replaceStatus(current, paid.status());
    }

    @PostMapping("/{orderId}/timeout")
    OrderDetail timeout(@PathVariable String orderId) {
        OrderDetail current = requireOrder(orderId);
        OrderSnapshot timeout = stateMachine.timeout(new OrderSnapshot(current.orderId(), current.status()));
        return replaceStatus(current, timeout.status());
    }

    private OrderDetail requireOrder(String orderId) {
        OrderDetail current = orders.get(orderId);
        if (current == null) {
            throw new IllegalArgumentException("order not found: " + orderId);
        }
        return current;
    }

    private OrderDetail replaceStatus(OrderDetail current, OrderStatus status) {
        OrderDetail updated = new OrderDetail(
            current.orderId(),
            current.tripId(),
            current.riderId(),
            current.seats(),
            current.amount(),
            status,
            current.createdAt()
        );
        orders.put(updated.orderId(), updated);
        return updated;
    }

    record CreateOrderRequest(String tripId, String riderId, int seats, BigDecimal amount) {
    }
}
