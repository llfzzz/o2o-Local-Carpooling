package com.o2o.carpooling.common.foundation;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryFixedWindowRateLimiter implements FixedWindowRateLimiter {

    // Distinct keys accumulate one entry each (per IP / user / phone). Without eviction the map grows
    // for the process lifetime — a slow leak that matters under a small heap. Once the map crosses
    // this size, opportunistically drop entries whose window has already elapsed.
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public InMemoryFixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean allow(String key, int limit, Duration window) {
        if (limit <= 0) {
            return false;
        }
        long windowSeconds = Math.max(1, window.toSeconds());
        long nowEpoch = clock.instant().getEpochSecond();
        long windowStart = (nowEpoch / windowSeconds) * windowSeconds;
        long expiresAt = windowStart + windowSeconds;
        if (counters.size() > CLEANUP_THRESHOLD) {
            counters.values().removeIf(existing -> existing.expiresAtEpochSeconds() <= nowEpoch);
        }
        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || current.windowStartEpochSeconds() != windowStart) {
                return new WindowCounter(windowStart, expiresAt, new AtomicInteger());
            }
            return current;
        });
        return counter.count().incrementAndGet() <= limit;
    }

    private record WindowCounter(long windowStartEpochSeconds, long expiresAtEpochSeconds, AtomicInteger count) {
    }
}
