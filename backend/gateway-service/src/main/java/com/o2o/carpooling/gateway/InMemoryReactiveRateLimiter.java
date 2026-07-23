package com.o2o.carpooling.gateway;

import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * In-memory (per-instance) reactive limiter — the single-instance/demo backend, and also the local
 * emergency limiter used when the Redis backend errors. The underlying computation is pure in-JVM
 * work, so wrapping it in {@code Mono.just} keeps it safe on the Netty event loop.
 */
class InMemoryReactiveRateLimiter implements GatewayRateLimiter {

    private final FixedWindowRateLimiter delegate;

    InMemoryReactiveRateLimiter(FixedWindowRateLimiter delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<RateLimitDecision> allow(String key, int limit, Duration window) {
        boolean allowed = delegate.allow(key, limit, window);
        return Mono.just(new RateLimitDecision(allowed, 0, Math.max(1, window.toSeconds())));
    }

    @Override
    public String backendName() {
        return "memory";
    }
}
