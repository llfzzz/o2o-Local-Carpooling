package com.o2o.carpooling.common.foundation;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryFixedWindowRateLimiter implements FixedWindowRateLimiter {

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
        long windowStart = (clock.instant().getEpochSecond() / windowSeconds) * windowSeconds;
        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || current.windowStartEpochSeconds != windowStart) {
                return new WindowCounter(windowStart, new AtomicInteger());
            }
            return current;
        });
        return counter.count.incrementAndGet() <= limit;
    }

    private record WindowCounter(long windowStartEpochSeconds, AtomicInteger count) {
    }
}
