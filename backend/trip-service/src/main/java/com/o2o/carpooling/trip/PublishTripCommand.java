package com.o2o.carpooling.trip;

import java.time.Instant;

record PublishTripCommand(
    String driverId,
    String originText,
    String destinationText,
    Instant departureAt,
    int distanceMeters,
    int durationSeconds,
    int totalSeats
) {
}
