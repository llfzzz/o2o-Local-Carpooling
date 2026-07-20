package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.GeoPoint;

import java.time.Instant;
import java.util.Objects;

/**
 * A geographic trip search. Both endpoints are resolved coordinates, not text — the whole point of
 * this type is that matching stops being a substring comparison on address strings.
 *
 * @param departAt  the rider's target departure; null means "any time"
 * @param minSeats  seats the rider needs; trips with fewer available are excluded
 */
record TripSearchQuery(GeoPoint origin, GeoPoint destination, Instant departAt, int minSeats) {

    TripSearchQuery {
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(destination, "destination is required");
        if (origin.datum() != destination.datum()) {
            throw new IllegalArgumentException("origin and destination must share a datum");
        }
        minSeats = Math.max(1, minSeats);
    }
}
