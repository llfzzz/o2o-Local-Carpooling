package com.o2o.carpooling.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Single-instance fallback when no Redis is configured (also used in unit tests). */
class InMemorySeenNonceStore implements SeenNonceStore {

    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemorySeenNonceStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean registerIfAbsent(String key, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        Instant previous = seen.compute(key, (unused, existing) ->
            existing != null && existing.isAfter(now) ? existing : expiresAt);
        // Newly registered iff the stored expiry is the one we just computed.
        return previous == expiresAt;
    }
}
