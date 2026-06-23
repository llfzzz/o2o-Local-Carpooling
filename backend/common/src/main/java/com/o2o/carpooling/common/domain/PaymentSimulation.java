package com.o2o.carpooling.common.domain;

import java.time.Instant;

public record PaymentSimulation(
    String paymentId,
    String orderId,
    Money amount,
    PaymentStatus status,
    Instant paidAt
) {
}
