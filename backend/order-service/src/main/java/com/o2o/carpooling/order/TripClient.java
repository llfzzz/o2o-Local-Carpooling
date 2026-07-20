package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.TripOffer;

import java.util.Optional;

interface TripClient {
    Optional<TripOffer> findTrip(String tripId);

    /** riderId is recorded on the lock so trip-service can gate live-location access on it. */
    TripOffer lockSeats(String tripId, String orderId, int seats, String riderId);

    TripOffer releaseSeats(String tripId, String orderId);
}
