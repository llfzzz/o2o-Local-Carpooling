package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.TripOffer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
class TripController {

    private final TripPublishService tripPublishService;
    private final TripRepository tripRepository;

    TripController(TripPublishService tripPublishService, TripRepository tripRepository) {
        this.tripPublishService = tripPublishService;
        this.tripRepository = tripRepository;
    }

    @PostMapping
    TripOffer publish(@RequestBody PublishTripRequest request) {
        return tripPublishService.publish(new PublishTripCommand(
            request.driverId(),
            request.originText(),
            request.destinationText(),
            request.city(),
            request.departureAt(),
            request.totalSeats()
        ));
    }

    @GetMapping
    List<TripOffer> search(@RequestParam(required = false) String origin, @RequestParam(required = false) String destination) {
        return tripRepository.search(origin, destination);
    }

    @GetMapping("/{tripId}")
    TripOffer get(@PathVariable String tripId) {
        return tripRepository.findByTripId(tripId)
            .orElseThrow(() -> new IllegalArgumentException("trip not found: " + tripId));
    }

    @PostMapping("/{tripId}/seat-locks")
    TripOffer lockSeats(@PathVariable String tripId, @RequestBody SeatLockRequest request) {
        return tripRepository.lockSeats(tripId, request.orderId(), request.seats());
    }

    @PostMapping("/{tripId}/seat-locks/{orderId}/release")
    TripOffer releaseSeats(@PathVariable String tripId, @PathVariable String orderId) {
        return tripRepository.releaseSeats(tripId, orderId);
    }

    record PublishTripRequest(
        String driverId,
        String originText,
        String destinationText,
        String city,
        Instant departureAt,
        Integer distanceMeters,
        Integer durationSeconds,
        int totalSeats
    ) {
    }

    record SeatLockRequest(String orderId, int seats) {
    }
}
