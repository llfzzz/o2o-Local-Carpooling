package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.TripOffer;

import java.util.Optional;

interface TripClient {
    Optional<TripOffer> findTrip(String tripId);

    TripOffer lockSeats(String tripId, String orderId, int seats);

    TripOffer releaseSeats(String tripId, String orderId);
}
