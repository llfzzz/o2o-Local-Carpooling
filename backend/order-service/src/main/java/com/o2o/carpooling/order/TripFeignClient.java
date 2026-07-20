package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.TripOffer;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Optional;

@FeignClient(name = "trip-service", url = "${O2O_TRIP_SERVICE_URL:http://127.0.0.1:8104}")
interface TripFeignClient {
    @GetMapping("/api/trips/{tripId}")
    TripOffer get(@PathVariable("tripId") String tripId);

    @PostMapping("/api/trips/{tripId}/seat-locks")
    TripOffer lockSeats(@PathVariable("tripId") String tripId, @RequestBody SeatLockRequest request);

    @PostMapping("/api/trips/{tripId}/seat-locks/{orderId}/release")
    TripOffer releaseSeats(@PathVariable("tripId") String tripId, @PathVariable("orderId") String orderId);

    record SeatLockRequest(String orderId, int seats, String riderId) {
    }
}

@Component
class FeignTripClient implements TripClient {

    private final TripFeignClient tripFeignClient;

    FeignTripClient(TripFeignClient tripFeignClient) {
        this.tripFeignClient = tripFeignClient;
    }

    @Override
    public Optional<TripOffer> findTrip(String tripId) {
        try {
            return Optional.of(tripFeignClient.get(tripId));
        } catch (FeignException.NotFound exception) {
            return Optional.empty();
        }
    }

    @Override
    public TripOffer lockSeats(String tripId, String orderId, int seats, String riderId) {
        return tripFeignClient.lockSeats(tripId, new TripFeignClient.SeatLockRequest(orderId, seats, riderId));
    }

    @Override
    public TripOffer releaseSeats(String tripId, String orderId) {
        return tripFeignClient.releaseSeats(tripId, orderId);
    }
}
