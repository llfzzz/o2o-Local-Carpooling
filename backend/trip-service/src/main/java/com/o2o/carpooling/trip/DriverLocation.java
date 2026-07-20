package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.GeoPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A driver's live position during an active trip.
 *
 * <p>Deliberately ephemeral — held in Redis under a short TTL and never written to MySQL. Precise
 * location history is a large, sensitive dataset with real retention obligations, and this product
 * does not need it: the only question it must answer is "where is my driver right now".
 */
record DriverLocation(
    String tripId,
    String driverId,
    GeoPoint point,
    Double headingDegrees,
    Double speedMetersPerSecond,
    Instant capturedAt
) {

    DriverLocation {
        Objects.requireNonNull(point, "point is required");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
        if (tripId == null || tripId.isBlank()) {
            throw new IllegalArgumentException("tripId is required");
        }
        if (driverId == null || driverId.isBlank()) {
            throw new IllegalArgumentException("driverId is required");
        }
    }

    boolean isStale(Instant now, Duration ttl) {
        return capturedAt.plus(ttl).isBefore(now);
    }

    Duration ageAt(Instant now) {
        return Duration.between(capturedAt, now);
    }
}
