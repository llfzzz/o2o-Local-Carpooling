package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;

import java.time.Instant;
import java.util.Optional;

/** Active when the Redis route cache is disabled: always a miss, never stores. */
class NoopRouteRedisCache implements RouteRedisCache {

    @Override
    public Optional<RouteSnapshot> get(String cacheKey) {
        return Optional.empty();
    }

    @Override
    public void put(String cacheKey, RouteSnapshot routeCore, Instant capturedAt) {
        // no-op
    }
}
