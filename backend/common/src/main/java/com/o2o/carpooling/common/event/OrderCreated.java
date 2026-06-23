package com.o2o.carpooling.common.event;

import java.time.Instant;

public record OrderCreated(String orderId, String tripId, String riderId, int seats, Instant occurredAt) {
}
