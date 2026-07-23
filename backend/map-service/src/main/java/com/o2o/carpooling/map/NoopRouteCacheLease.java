package com.o2o.carpooling.map;

import java.util.Optional;

/**
 * Active when the Redis cache is disabled (single-instance / demo). The caller is always the sole
 * filler; cross-instance coordination is unnecessary and the in-process single-flight already dedups.
 */
class NoopRouteCacheLease implements RouteCacheLease {

    private static final Optional<String> ALWAYS_ACQUIRED = Optional.of("noop");

    @Override
    public Optional<String> tryAcquire(String cacheKey) {
        return ALWAYS_ACQUIRED;
    }

    @Override
    public void release(String cacheKey, String ownerToken) {
        // no-op
    }
}
