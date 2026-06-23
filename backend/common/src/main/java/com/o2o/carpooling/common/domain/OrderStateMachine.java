package com.o2o.carpooling.common.domain;

public final class OrderStateMachine {

    public OrderSnapshot pay(OrderSnapshot order) {
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("order " + order.orderId() + " cannot be paid from " + order.status());
        }
        return new OrderSnapshot(order.orderId(), OrderStatus.SEAT_LOCKED);
    }

    public OrderSnapshot timeout(OrderSnapshot order) {
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("order " + order.orderId() + " cannot timeout from " + order.status());
        }
        return new OrderSnapshot(order.orderId(), OrderStatus.TIMEOUT_CANCELLED);
    }
}
