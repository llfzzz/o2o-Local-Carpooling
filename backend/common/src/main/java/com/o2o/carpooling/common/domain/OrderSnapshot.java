package com.o2o.carpooling.common.domain;

import java.util.Objects;

public record OrderSnapshot(String orderId, OrderStatus status) {

    public OrderSnapshot {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        Objects.requireNonNull(status, "status is required");
    }
}
