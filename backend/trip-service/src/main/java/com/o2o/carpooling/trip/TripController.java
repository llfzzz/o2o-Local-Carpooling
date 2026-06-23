package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PricingPolicy;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.domain.SeatInventory;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.domain.TripStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/trips")
class TripController {

    private final Map<String, TripOffer> trips = new ConcurrentHashMap<>();
    private final PricingPolicy pricingPolicy = new PricingPolicy(new BigDecimal("6.00"), new BigDecimal("1.20"));

    @PostMapping
    TripOffer publish(@RequestBody PublishTripRequest request) {
        RouteSnapshot route = new RouteSnapshot("route-" + UUID.randomUUID(), request.distanceMeters(), request.durationSeconds(), "amap-mock");
        Money price = pricingPolicy.quote(route);
        TripOffer trip = new TripOffer(
            "trip-" + UUID.randomUUID(),
            request.driverId(),
            request.originText(),
            request.destinationText(),
            request.departureAt(),
            route,
            new SeatInventory("pending", request.totalSeats(), 0),
            price,
            TripStatus.PUBLISHED
        );
        TripOffer persisted = new TripOffer(
            trip.tripId(),
            trip.driverId(),
            trip.originText(),
            trip.destinationText(),
            trip.departureAt(),
            trip.route(),
            new SeatInventory(trip.tripId(), request.totalSeats(), 0),
            trip.seatPrice(),
            trip.status()
        );
        trips.put(persisted.tripId(), persisted);
        return persisted;
    }

    @GetMapping
    List<TripOffer> search(@RequestParam(required = false) String origin, @RequestParam(required = false) String destination) {
        return new ArrayList<>(trips.values()).stream()
            .filter(trip -> origin == null || trip.originText().contains(origin))
            .filter(trip -> destination == null || trip.destinationText().contains(destination))
            .toList();
    }

    record PublishTripRequest(
        String driverId,
        String originText,
        String destinationText,
        Instant departureAt,
        int distanceMeters,
        int durationSeconds,
        int totalSeats
    ) {
    }
}
