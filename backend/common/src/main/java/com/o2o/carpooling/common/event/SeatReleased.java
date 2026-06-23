package com.o2o.carpooling.common.event;

import java.time.Instant;

public record SeatReleased(String tripId, String orderId, int seats, Instant occurredAt) {
}
