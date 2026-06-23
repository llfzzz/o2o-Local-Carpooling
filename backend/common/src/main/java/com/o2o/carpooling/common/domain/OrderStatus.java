package com.o2o.carpooling.common.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    SEAT_LOCKED,
    USER_CANCELLED,
    DRIVER_CANCELLED,
    TIMEOUT_CANCELLED,
    COMPLETED
}
