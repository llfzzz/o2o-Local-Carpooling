package com.o2o.carpooling.gateway;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Non-blocking rate limiter for the WebFlux gateway. Implementations must never block the Netty event
 * loop: the Redis-backed one runs an atomic Lua script reactively; the in-memory one is a pure
 * in-JVM computation wrapped in {@code Mono.just}.
 */
interface GatewayRateLimiter {

    Mono<RateLimitDecision> allow(String key, int limit, Duration window);

    /** Low-cardinality backend label for metrics: {@code "redis"} or {@code "memory"}. */
    String backendName();
}
