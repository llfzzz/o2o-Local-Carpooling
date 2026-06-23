package com.o2o.carpooling.common.event;

import java.time.Instant;

public record OrderPaymentTimedOut(String orderId, Instant occurredAt) {
}
