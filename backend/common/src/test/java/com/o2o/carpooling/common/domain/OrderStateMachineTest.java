package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void paidOrderMovesFromPendingPaymentToLockedSeat() {
        OrderSnapshot order = new OrderSnapshot("order-1", OrderStatus.PENDING_PAYMENT);

        OrderSnapshot paid = stateMachine.pay(order);

        assertThat(paid.status()).isEqualTo(OrderStatus.SEAT_LOCKED);
    }

    @Test
    void timeoutReleasesOnlyPendingPaymentOrders() {
        OrderSnapshot order = new OrderSnapshot("order-2", OrderStatus.PENDING_PAYMENT);

        OrderSnapshot cancelled = stateMachine.timeout(order);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.TIMEOUT_CANCELLED);
    }

    @Test
    void cannotPayCancelledOrder() {
        OrderSnapshot order = new OrderSnapshot("order-3", OrderStatus.USER_CANCELLED);

        assertThatThrownBy(() -> stateMachine.pay(order))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot be paid");
    }
}
