package com.o2o.carpooling.gateway;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Distributed fixed-window limiter on the STATE Redis. One atomic Lua round-trip per request:
 * increment the counter, set the window TTL on the first hit, and return the current count plus the
 * remaining TTL so {@code Retry-After} reflects the true window remainder. Runs entirely reactively —
 * no blocking Redis call ever touches the Netty event loop.
 */
class RedisReactiveRateLimiter implements GatewayRateLimiter {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT = RedisScript.of("""
        local current = redis.call('incr', KEYS[1])
        local ttl
        if current == 1 then
          redis.call('pexpire', KEYS[1], ARGV[1])
          ttl = tonumber(ARGV[1])
        else
          ttl = redis.call('pttl', KEYS[1])
        end
        return {current, ttl}
        """, List.class);

    private final ReactiveStringRedisTemplate redis;

    RedisReactiveRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Mono<RateLimitDecision> allow(String key, int limit, Duration window) {
        if (limit <= 0) {
            return Mono.just(new RateLimitDecision(false, 0, Math.max(1, window.toSeconds())));
        }
        long windowMillis = Math.max(1, window.toMillis());
        return redis.execute(SCRIPT, List.of(key), List.of(Long.toString(windowMillis)))
            .singleOrEmpty()
            .map(raw -> {
                List result = (List) raw;
                long current = ((Number) result.get(0)).longValue();
                long ttlMillis = ((Number) result.get(1)).longValue();
                long retryAfter = ttlMillis <= 0 ? Math.max(1, window.toSeconds()) : (ttlMillis + 999) / 1000;
                return new RateLimitDecision(current <= limit, current, retryAfter);
            });
        // A Redis failure surfaces as an error signal; GatewaySecurityFilter applies the degraded policy.
    }

    @Override
    public String backendName() {
        return "redis";
    }
}
