package com.o2o.carpooling.gateway;

import com.o2o.carpooling.common.foundation.InMemoryFixedWindowRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Clock;

/**
 * Selects the gateway's primary rate limiter. {@code security.rate-limit.backend=redis} wires the
 * distributed Redis limiter — and because that bean requires a {@link ReactiveStringRedisTemplate}, a
 * misconfigured Redis backend (e.g. the reactive Redis starter missing) fails startup loudly instead
 * of silently falling back to the in-memory backend. Anything else uses the in-memory limiter.
 */
@Configuration
class GatewayRateLimiterConfig {

    @Bean
    @ConditionalOnProperty(prefix = "security.rate-limit", name = "backend", havingValue = "redis")
    GatewayRateLimiter redisGatewayRateLimiter(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        return new RedisReactiveRateLimiter(reactiveStringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(GatewayRateLimiter.class)
    GatewayRateLimiter memoryGatewayRateLimiter(Clock clock) {
        return new InMemoryReactiveRateLimiter(new InMemoryFixedWindowRateLimiter(clock));
    }

    /**
     * The degraded-mode local emergency limiter — always in-memory, kept as its own bean so it can
     * never accidentally resolve to a blocking Redis limiter on the Netty event loop.
     */
    @Bean
    InMemoryFixedWindowRateLimiter gatewayEmergencyRateLimiter(Clock clock) {
        return new InMemoryFixedWindowRateLimiter(clock);
    }
}
