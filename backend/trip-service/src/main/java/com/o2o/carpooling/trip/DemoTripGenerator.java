package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Demo virtual-trip generation (demo profile only, gated by {@link DemoEndpoints}).
 *
 * <p>The "random route" flow is a real, validated pipeline — not random text or invented numbers:
 * it picks two structured fixture POIs from the SAME city, asks map-service for an authoritative
 * route quote, and accepts the pair only when the authoritative distance falls inside a configured
 * range (preferring a tighter band), retrying other pairs and failing with a clear error if none
 * qualifies. Every accepted route then yields a set of virtual offers priced by the SAME
 * {@link com.o2o.carpooling.common.domain.PricingPolicy} as real trips.
 *
 * <p>Safety: synthetic {@code demo-driver-N} ids can never authenticate; rows are labelled DEMO;
 * generation is deterministic per seed; regeneration replaces same-route offers rather than
 * accumulating; an hourly job expires departed demo trips. Generated trips are persisted with the
 * selected coordinates and departure inside the matching window, so the NORMAL trip-search API
 * returns them — the UI never renders fabricated cards.
 */
@Service
class DemoTripGenerator {

    private static final int GENERATIONS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final TripRepository tripRepository;
    private final MapClient mapClient;
    private final TripMatchingProperties properties;
    private final FixedWindowRateLimiter rateLimiter;
    private final Clock clock;

    DemoTripGenerator(TripRepository tripRepository, MapClient mapClient, TripMatchingProperties properties,
                      FixedWindowRateLimiter rateLimiter, Clock clock) {
        this.tripRepository = tripRepository;
        this.mapClient = mapClient;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Generate offers for a rider-selected route (explicit origin + destination). */
    @Transactional
    GeneratedDemoTrips generate(String userId, LocationRef origin, LocationRef destination, Long seed) {
        rateLimit(userId);
        RouteSnapshot route = mapClient.quoteRoute(origin, destination);
        return generateForRoute(userId, route, seed);
    }

    /**
     * Generate offers for a "random route": two distinct fixture places from one supported city,
     * validated against the configured distance range with bounded retries.
     */
    @Transactional
    GeneratedDemoTrips generateRandom(String userId, String cityCode, Long seed) {
        rateLimit(userId);
        List<LocationRef> pool = poolForCity(cityCode, seed);
        TripMatchingProperties.Demo demo = properties.getDemo();
        Random random = new Random(seed != null ? seed : System.nanoTime());

        Accepted preferred = null;
        Accepted inRange = null;
        for (int attempt = 0; attempt < demo.getMaxAttempts() && preferred == null; attempt++) {
            int i = random.nextInt(pool.size());
            int j = random.nextInt(pool.size() - 1);
            if (j >= i) {
                j++;
            }
            LocationRef origin = pool.get(i);
            LocationRef destination = pool.get(j);
            RouteSnapshot route = mapClient.quoteRoute(origin, destination);
            int distance = route.distanceMeters();
            if (demo.inPreferredBand(distance)) {
                preferred = new Accepted(route);
            } else if (demo.inRange(distance) && inRange == null) {
                inRange = new Accepted(route);
            }
        }

        Accepted chosen = preferred != null ? preferred : inRange;
        if (chosen == null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DEMO_NO_VALID_ROUTE",
                "无法在配置的距离范围内生成随机路线，请重试或更换城市");
        }
        return generateForRoute(userId, chosen.route(), seed);
    }

    /** Hourly cleanup so demo data cannot pile up unbounded. */
    @Scheduled(fixedDelayString = "${trip.demo.cleanup-fixed-delay:PT1H}")
    void cleanupExpiredDemoTrips() {
        tripRepository.deleteExpiredDemoTrips(clock.instant().minus(properties.getDemo().getRetention()));
    }

    /**
     * Turn an already-quoted authoritative route into a set of persisted, searchable virtual
     * offers. The route is quoted exactly once by the caller so a random-route retry and the
     * generation use the identical distance/duration/geometry.
     */
    private GeneratedDemoTrips generateForRoute(String userId, RouteSnapshot route, Long seed) {
        LocationRef origin = route.origin();
        LocationRef destination = route.destination();
        if (origin == null || destination == null || origin.point() == null || destination.point() == null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DEMO_ROUTE_UNRESOLVED",
                "route quote did not return structured endpoints");
        }
        TripMatchingProperties.Demo demo = properties.getDemo();
        Instant now = clock.instant();
        Random random = new Random(effectiveSeed(seed, origin, destination));

        // Replace, don't accumulate: drop this route's future demo offers before regenerating.
        tripRepository.deleteFutureDemoTripsByEndpoints(origin.displayName(), destination.displayName(), now);

        long spreadSeconds = Math.max(1, demo.getDepartureSpread().toSeconds());
        List<TripOffer> offers = new ArrayList<>();
        for (int i = 0; i < demo.getOffers(); i++) {
            // Departure inside [lead, lead+spread] so the normal trip-search time window returns
            // it; seats 1..4. Price is NOT varied — it stays the formula-derived per-seat fare.
            Instant departureAt = now.plus(demo.getDepartureLead())
                .plusSeconds((long) (random.nextDouble() * spreadSeconds));
            int totalSeats = 1 + random.nextInt(4);
            PublishTripCommand command = new PublishTripCommand(
                "demo-driver-" + (i + 1),
                origin.displayName(),
                destination.displayName(),
                null,
                departureAt,
                totalSeats,
                null,
                origin,
                destination
            );
            offers.add(tripRepository.createDemo(command, route));
        }
        return new GeneratedDemoTrips(origin, destination, route, offers);
    }

    /**
     * Places for one supported city. An explicit cityCode is used directly; when absent, a city
     * with at least two fixture places is chosen deterministically from the seed — so the two
     * endpoints always belong to the same city.
     */
    private List<LocationRef> poolForCity(String cityCode, Long seed) {
        if (StringUtils.hasText(cityCode)) {
            List<LocationRef> places = mapClient.demoPlaces(cityCode);
            if (places.size() < 2) {
                throw tooFewPlaces();
            }
            return places;
        }
        Map<String, List<LocationRef>> byCity = new LinkedHashMap<>();
        for (LocationRef place : mapClient.demoPlaces(null)) {
            byCity.computeIfAbsent(place.cityCode(), key -> new ArrayList<>()).add(place);
        }
        List<List<LocationRef>> eligible = byCity.values().stream()
            .filter(places -> places.size() >= 2)
            .toList();
        if (eligible.isEmpty()) {
            throw tooFewPlaces();
        }
        int index = Math.floorMod(seed != null ? seed.intValue() : (int) System.nanoTime(), eligible.size());
        return eligible.get(index);
    }

    private BusinessException tooFewPlaces() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "DEMO_CITY_TOO_FEW_PLACES",
            "该城市的演示地点不足以生成随机路线");
    }

    private void rateLimit(String userId) {
        if (!rateLimiter.allow("trip:demo-generate:" + userId, GENERATIONS_PER_WINDOW, WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                "DEMO_TRIP_RATE_LIMITED", "生成演示行程过于频繁，请稍后再试");
        }
    }

    /** A stable seed so unseeded regeneration for the same endpoints is still deterministic. */
    private long effectiveSeed(Long seed, LocationRef origin, LocationRef destination) {
        if (seed != null) {
            return seed;
        }
        return (long) (origin.displayName() + "->" + destination.displayName()).hashCode();
    }

    private record Accepted(RouteSnapshot route) {
    }

    /** Envelope: the chosen authoritative route plus its persisted, searchable virtual offers. */
    record GeneratedDemoTrips(LocationRef origin, LocationRef destination, RouteSnapshot route, List<TripOffer> offers) {
    }
}
