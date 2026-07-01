package com.o2o.carpooling.common.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Authoritative order transitions. Every status change must go through here so illegal moves
 * (e.g. paying a cancelled order, completing an unpaid one, or cancelling a terminal one) are
 * rejected consistently. Cancellations and completion only apply while the order is still active
 * (PENDING_PAYMENT or the paid SEAT_LOCKED state); terminal states cannot transition further.
 */
public final class OrderStateMachine {

    /** States from which a rider/driver/operator may still cancel and release the seat. */
    private static final Set<OrderStatus> CANCELLABLE =
        EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.SEAT_LOCKED);

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

    public OrderSnapshot cancelByUser(OrderSnapshot order) {
        return cancel(order, OrderStatus.USER_CANCELLED);
    }

    public OrderSnapshot cancelByDriver(OrderSnapshot order) {
        return cancel(order, OrderStatus.DRIVER_CANCELLED);
    }

    public OrderSnapshot cancelByOperator(OrderSnapshot order) {
        return cancel(order, OrderStatus.OPERATOR_CANCELLED);
    }

    public OrderSnapshot complete(OrderSnapshot order) {
        if (order.status() != OrderStatus.SEAT_LOCKED) {
            throw new IllegalStateException("order " + order.orderId() + " cannot complete from " + order.status());
        }
        return new OrderSnapshot(order.orderId(), OrderStatus.COMPLETED);
    }

    private OrderSnapshot cancel(OrderSnapshot order, OrderStatus target) {
        if (!CANCELLABLE.contains(order.status())) {
            throw new IllegalStateException("order " + order.orderId() + " cannot be cancelled from " + order.status());
        }
        return new OrderSnapshot(order.orderId(), target);
    }
}
