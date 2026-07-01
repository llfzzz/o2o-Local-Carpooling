package com.o2o.carpooling.payment;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/** Redis-backed nonce store so replay protection holds across multiple service instances. */
class RedisSeenNonceStore implements SeenNonceStore {

    private static final String PREFIX = "payment:callback:nonce:";

    private final StringRedisTemplate redis;

    RedisSeenNonceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean registerIfAbsent(String key, Duration ttl) {
        Boolean stored = redis.opsForValue().setIfAbsent(PREFIX + key, "1", ttl);
        return Boolean.TRUE.equals(stored);
    }
}
