package com.o2o.carpooling.common.domain;

import java.time.Instant;

public record OrderDetail(
    String orderId,
    String tripId,
    String riderId,
    int seats,
    Money amount,
    OrderStatus status,
    Instant createdAt
) {
}
