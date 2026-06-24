package com.o2o.carpooling.trip;

import java.time.Instant;

record PublishTripCommand(
    String driverId,
    String originText,
    String destinationText,
    String city,
    Instant departureAt,
    int totalSeats
) {
}
