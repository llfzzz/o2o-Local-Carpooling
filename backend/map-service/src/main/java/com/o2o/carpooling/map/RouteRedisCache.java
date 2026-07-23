package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;

import java.time.Instant;
import java.util.Optional;

/**
 * Optional Redis read-cache for structured route facts (distance / duration / polyline / provider
 * trace). It caches only derived, reconstructable data — never identity, order, seat, payment,
 * authorization or mutable-availability state — and every failure degrades to a cache miss so the
 * MySQL/provider path stays authoritative. Endpoints are never cached: they are request-specific and
 * are re-applied per caller.
 */
interface RouteRedisCache {

    /** Fresh cached route core (endpoints null), or empty on miss / Redis error / decode failure. */
    Optional<RouteSnapshot> get(String cacheKey);

    /**
     * Cache the route core. TTL is base + jitter, capped by the source snapshot's remaining freshness
     * (from {@code capturedAt}) so Redis never outlives the MySQL fresh window; oversized or
     * already-stale entries are skipped. Never throws — a cache write failure is not fatal.
     */
    void put(String cacheKey, RouteSnapshot routeCore, Instant capturedAt);
}
