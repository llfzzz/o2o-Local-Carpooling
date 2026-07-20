package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.LocationRef;

import java.time.Instant;

/**
 * @param driverId resolved from the authenticated principal ({@code X-User-Id}), never from the
 *                 request body — a client must not be able to publish as somebody else.
 */
record PublishTripCommand(
    String driverId,
    String originText,
    String destinationText,
    String city,
    Instant departureAt,
    int totalSeats,
    String idempotencyKey,
    LocationRef origin,
    LocationRef destination
) {

    /** Legacy text form; the provider geocodes the strings. */
    static PublishTripCommand ofText(
        String driverId, String originText, String destinationText, String city,
        Instant departureAt, int totalSeats, String idempotencyKey
    ) {
        return new PublishTripCommand(
            driverId, originText, destinationText, city, departureAt, totalSeats, idempotencyKey, null, null);
    }

    /** True when both endpoints are already resolved places. */
    boolean isResolved() {
        return origin != null && destination != null;
    }
}
