package com.o2o.carpooling.common.event;

import java.time.Instant;

public record TripPublished(String tripId, String driverId, Instant occurredAt) {
}
