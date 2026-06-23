package com.o2o.carpooling.common.event;

import java.time.Instant;

public record OrderCancelled(String orderId, String reason, Instant occurredAt) {
}
