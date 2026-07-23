package com.o2o.carpooling.map;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Selects the Redis route-cache + cache-fill lease implementations when
 * {@code map.route-cache.redis.enabled=true} (a cache Redis is deployed), or safe no-ops otherwise.
 * The no-ops keep the whole feature optional: with the cache disabled, map-service behaves exactly as
 * before (MySQL snapshot + provider + in-process single-flight) and never touches Redis.
 */
@Configuration
class MapCacheConfig {

    @Bean
    @ConditionalOnProperty(prefix = "map.route-cache.redis", name = "enabled", havingValue = "true")
    RouteRedisCache redisRouteCache(StringRedisTemplate redisTemplate, MapResilienceProperties resilience,
                                    MeterRegistry meterRegistry) {
        return new RedisRouteCache(redisTemplate, resilience, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(RouteRedisCache.class)
    RouteRedisCache noopRouteRedisCache() {
        return new NoopRouteRedisCache();
    }

    @Bean
    @ConditionalOnProperty(prefix = "map.route-cache.redis", name = "enabled", havingValue = "true")
    RouteCacheLease redisRouteCacheLease(StringRedisTemplate redisTemplate, MapResilienceProperties resilience,
                                         MeterRegistry meterRegistry) {
        return new RedisRouteCacheLease(redisTemplate, resilience, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(RouteCacheLease.class)
    RouteCacheLease noopRouteCacheLease() {
        return new NoopRouteCacheLease();
    }
}
