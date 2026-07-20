package com.o2o.carpooling.trip;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/**
 * Wires live-location storage. Prefers Redis (positions must be visible across instances) and
 * falls back to in-memory for single-instance and test runs — the same selection the SMS-code,
 * refresh-token and payment-nonce stores use.
 */
@Configuration
class TripTrackingConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    DriverPresenceStore redisDriverPresenceStore(
        StringRedisTemplate redisTemplate,
        TripMatchingProperties properties
    ) {
        return new RedisDriverPresenceStore(redisTemplate, properties.getTracking().getPresenceTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    DriverPresenceStore inMemoryDriverPresenceStore(Clock clock, TripMatchingProperties properties) {
        return new InMemoryDriverPresenceStore(clock, properties.getTracking().getPresenceTtl());
    }
}
