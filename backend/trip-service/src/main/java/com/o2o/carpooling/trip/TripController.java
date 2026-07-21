package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.CoordinateTransform;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final RoutePreviewService routePreviewService;

    TripController(TripPublishService tripPublishService, TripRepository tripRepository,
                   RoutePreviewService routePreviewService) {
        this.tripPublishService = tripPublishService;
        this.tripRepository = tripRepository;
        this.routePreviewService = routePreviewService;
    }

    /**
     * The publishing driver is the authenticated principal, full stop. The Gateway strips any
     * client-supplied {@code X-User-Id} and re-injects the one it verified from the token, so this
     * header is trustworthy in a way the request body never was.
     */
    @PostMapping
    TripOffer publish(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody PublishTripRequest request
    ) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED",
                "authentication is required to publish a trip");
        }
        return tripPublishService.publish(new PublishTripCommand(
            currentUserId,
            request.originText(),
            request.destinationText(),
            request.city(),
            request.departureAt(),
            request.totalSeats(),
            request.idempotencyKey(),
            request.origin(),
            request.destination()
        ));
    }

    /**
     * Geographic search: trips whose start and end are near the rider's, departing within the
     * configured window, with seats to spare — ranked by how little detour they imply.
     */
    @GetMapping("/search")
    List<TripOffer> searchByProximity(
        @RequestParam double originLat,
        @RequestParam double originLng,
        @RequestParam double destinationLat,
        @RequestParam double destinationLng,
        @RequestParam(required = false) CoordinateDatum datum,
        @RequestParam(required = false) Instant departAt,
        @RequestParam(required = false, defaultValue = "1") int minSeats
    ) {
        CoordinateDatum effectiveDatum = datum == null ? CoordinateDatum.GCJ02 : datum;
        // Trips are stored in the provider datum, so a WGS-84 query is converted before comparing.
        GeoPoint origin = CoordinateTransform.toDatum(
            new GeoPoint(originLat, originLng, effectiveDatum), CoordinateDatum.GCJ02);
        GeoPoint destination = CoordinateTransform.toDatum(
            new GeoPoint(destinationLat, destinationLng, effectiveDatum), CoordinateDatum.GCJ02);
        return tripRepository.searchByProximity(new TripSearchQuery(origin, destination, departAt, minSeats));
    }


    /**
     * Route confirmation for the interactive map: authoritative route + per-seat fare breakdown
     * from the same server-side pricing policy that prices published trips.
     */
    @PostMapping("/route-preview")
    RoutePreviewService.RoutePreview routePreview(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody RoutePreviewRequest request
    ) {
        return routePreviewService.preview(currentUserId, request.origin(), request.destination());
    }

    @GetMapping("/{tripId}")
    TripOffer get(@PathVariable String tripId) {
        return tripRepository.findByTripId(tripId)
            .orElseThrow(() -> new IllegalArgumentException("trip not found: " + tripId));
    }

    @PostMapping("/{tripId}/seat-locks")
    TripOffer lockSeats(@PathVariable String tripId, @RequestBody SeatLockRequest request) {
        // riderId comes from order-service, which resolved it from the authenticated principal.
        // This endpoint is internal-only (the Gateway 404s it), so it is not client input.
        return tripRepository.lockSeats(tripId, request.orderId(), request.seats(), request.riderId());
    }

    @PostMapping("/{tripId}/seat-locks/{orderId}/release")
    TripOffer releaseSeats(@PathVariable String tripId, @PathVariable String orderId) {
        return tripRepository.releaseSeats(tripId, orderId);
    }

    /**
     * @param origin      resolved start; preferred. When present, no geocoding round-trip happens.
     * @param destination resolved end; preferred.
     * @param originText  free text, geocoded server-side when {@code origin} is absent. Still
     *                    yields a resolved {@link LocationRef} on the stored trip — resolution
     *                    just happens on the server rather than in the client.
     */
    record PublishTripRequest(
        String originText,
        String destinationText,
        String city,
        Instant departureAt,
        int totalSeats,
        String idempotencyKey,
        LocationRef origin,
        LocationRef destination
    ) {
    }

    record SeatLockRequest(String orderId, int seats, String riderId) {
    }

    record RoutePreviewRequest(LocationRef origin, LocationRef destination) {
    }
}
