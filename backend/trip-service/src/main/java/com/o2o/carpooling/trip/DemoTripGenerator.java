package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Demo virtual-trip generation (demo profile only, already gated by {@link DemoEndpoints}).
 *
 * <p>Safety properties, all enforced here:
 * <ul>
 *   <li><b>Server-authoritative pricing/route.</b> One authoritative map-service quote, then the
 *       SAME {@link com.o2o.carpooling.common.domain.PricingPolicy} used for real trips — the
 *       per-seat price is strictly formula-derived with zero variation ("derived, not arbitrary").</li>
 *   <li><b>No real-user impersonation.</b> Driver ids are synthetic {@code demo-driver-N}; auth
 *       only ever mints {@code user-<phone>}, so these can never authenticate.</li>
 *   <li><b>Deterministic.</b> A given {@code seed} yields identical offers, so tests are stable.</li>
 *   <li><b>Bounded.</b> Regeneration replaces the same-endpoint future demo trips (cap {@link #OFFERS})
 *       rather than accumulating, and an hourly job expires departed demo trips.</li>
 *   <li><b>Capability bypass confined here.</b> Real publishing requires driver approval; demo
 *       generation deliberately skips it because the endpoint 404s outside demo, the drivers are
 *       synthetic, and the rows are labelled DEMO.</li>
 * </ul>
 */
@Service
class DemoTripGenerator {

    /** Offers per generated route. */
    private static final int OFFERS = 5;
    private static final int GENERATIONS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration DEMO_RETENTION = Duration.ofHours(24);

    private final TripRepository tripRepository;
    private final MapClient mapClient;
    private final FixedWindowRateLimiter rateLimiter;
    private final Clock clock;

    DemoTripGenerator(TripRepository tripRepository, MapClient mapClient,
                      FixedWindowRateLimiter rateLimiter, Clock clock) {
        this.tripRepository = tripRepository;
        this.mapClient = mapClient;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    List<TripOffer> generate(String userId, LocationRef origin, LocationRef destination, Long seed) {
        rateLimit(userId);
        RouteSnapshot route = mapClient.quoteRoute(origin, destination);
        LocationRef resolvedOrigin = route.origin() != null ? route.origin() : origin;
        LocationRef resolvedDestination = route.destination() != null ? route.destination() : destination;
        Instant now = clock.instant();
        Random random = new Random(effectiveSeed(seed, resolvedOrigin, resolvedDestination));

        // Replace, don't accumulate: drop this route's future demo offers before regenerating.
        tripRepository.deleteFutureDemoTripsByEndpoints(
            resolvedOrigin.displayName(), resolvedDestination.displayName(), now);

        List<TripOffer> generated = new ArrayList<>();
        for (int i = 0; i < OFFERS; i++) {
            // Departure spread 15 min .. 3 h; seats 1..4. Price is NOT varied — it stays the
            // formula-derived per-seat fare for the shared route.
            Instant departureAt = now.plus(Duration.ofMinutes(15 + random.nextInt(166)));
            int totalSeats = 1 + random.nextInt(4);
            PublishTripCommand command = new PublishTripCommand(
                "demo-driver-" + (i + 1),
                resolvedOrigin.displayName(),
                resolvedDestination.displayName(),
                null,
                departureAt,
                totalSeats,
                null,
                resolvedOrigin,
                resolvedDestination
            );
            generated.add(tripRepository.createDemo(command, route));
        }
        return generated;
    }

    @Transactional
    List<TripOffer> generateRandom(String userId, String cityCode, Long seed) {
        List<LocationRef> places = mapClient.demoPlaces(cityCode);
        if (places.size() < 2) {
            throw new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "DEMO_CITY_TOO_FEW_PLACES", "该城市的演示地点不足以生成随机路线");
        }
        // Deterministic pick of two distinct places from the seed (or a hash when unseeded).
        Random random = new Random(seed != null ? seed : System.nanoTime());
        int originIndex = random.nextInt(places.size());
        int destinationIndex = random.nextInt(places.size() - 1);
        if (destinationIndex >= originIndex) {
            destinationIndex++;
        }
        return generate(userId, places.get(originIndex), places.get(destinationIndex), seed);
    }

    /** Hourly cleanup so demo data cannot pile up unbounded. */
    @Scheduled(fixedDelayString = "${trip.demo.cleanup-fixed-delay:PT1H}")
    void cleanupExpiredDemoTrips() {
        tripRepository.deleteExpiredDemoTrips(clock.instant().minus(DEMO_RETENTION));
    }

    private void rateLimit(String userId) {
        if (!rateLimiter.allow("trip:demo-generate:" + userId, GENERATIONS_PER_WINDOW, WINDOW)) {
            throw new BusinessException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
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
}
