package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demo virtual-trip generation. Gateway-routed and JWT-protected (identity = injected X-User-Id),
 * but demo-only: {@link DemoEndpoints#requireVirtualTrips()} returns 404 outside the demo profile,
 * where DemoModeGuard guarantees the flag can never be true. Generated trips are labelled DEMO and
 * priced by the same server-side policy as real trips.
 */
@RestController
@RequestMapping("/api/demo/trips")
class DemoTripController {

    private final DemoTripGenerator generator;
    private final DemoEndpoints demoEndpoints;

    DemoTripController(DemoTripGenerator generator, DemoEndpoints demoEndpoints) {
        this.generator = generator;
        this.demoEndpoints = demoEndpoints;
    }

    /** Generate a set of virtual offers for a rider-selected route. */
    @PostMapping("/generate")
    List<TripOffer> generate(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody GenerateRequest request
    ) {
        demoEndpoints.requireVirtualTrips();
        requireUser(currentUserId);
        if (request == null || request.origin() == null || request.destination() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DEMO_TRIP_ENDPOINTS_REQUIRED",
                "origin and destination are required");
        }
        return generator.generate(currentUserId, request.origin(), request.destination(), request.seed());
    }

    /** Generate offers for a "random route" — two distinct fixture places in the given city. */
    @PostMapping("/random")
    List<TripOffer> random(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody RandomRequest request
    ) {
        demoEndpoints.requireVirtualTrips();
        requireUser(currentUserId);
        return generator.generateRandom(currentUserId, request == null ? null : request.cityCode(),
            request == null ? null : request.seed());
    }

    private void requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "authentication is required");
        }
    }

    record GenerateRequest(LocationRef origin, LocationRef destination, Long seed) {
    }

    record RandomRequest(String cityCode, Long seed) {
    }
}
