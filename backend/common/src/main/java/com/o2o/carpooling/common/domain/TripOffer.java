package com.o2o.carpooling.common.domain;

import java.time.Instant;

public record TripOffer(
    String tripId,
    String driverId,
    String originText,
    String destinationText,
    Instant departureAt,
    RouteSnapshot route,
    SeatInventory inventory,
    Money seatPrice,
    TripStatus status
) {
}
