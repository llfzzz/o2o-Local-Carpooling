package com.o2o.carpooling.map;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cache-fill lease on the cache Redis. Acquire is an atomic {@code SET key token NX PX ttl}; release
 * is an atomic owner-compare-and-delete Lua script, so a slow owner whose lease already expired (and
 * was re-acquired by another instance) cannot delete someone else's lease.
 */
class RedisRouteCacheLease implements RouteCacheLease {

    private static final String KEY_PREFIX = "cache:map:route:lease:v1:";

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redis;
    private final MapResilienceProperties resilience;
    private final MeterRegistry meterRegistry;
    private final SecureRandom random = new SecureRandom();

    RedisRouteCacheLease(StringRedisTemplate redis, MapResilienceProperties resilience, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.resilience = resilience;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<String> tryAcquire(String cacheKey) {
        if (cacheKey == null) {
            return Optional.empty();
        }
        String token = newToken();
        Duration leaseTtl = resilience.getRouteCache().getRedis().getLeaseTtl();
        try {
            Boolean acquired = redis.opsForValue()
                .setIfAbsent(leaseKey(cacheKey), token, leaseTtl.toMillis(), TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(acquired)) {
                lease("acquired");
                return Optional.of(token);
            }
            lease("contended");
            return Optional.empty();
        } catch (RuntimeException redisError) {
            lease("error"); // treat as not-acquired; the caller falls back to the safe load path
            return Optional.empty();
        }
    }

    @Override
    public void release(String cacheKey, String ownerToken) {
        if (cacheKey == null || ownerToken == null) {
            return;
        }
        try {
            redis.execute(RELEASE, List.of(leaseKey(cacheKey)), ownerToken);
        } catch (RuntimeException ignored) {
            // best-effort; the lease PX guarantees eventual expiry regardless
        }
    }

    private String newToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String leaseKey(String cacheKey) {
        return KEY_PREFIX + sha256(cacheKey);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("sha-256 unavailable", e);
        }
    }

    private void lease(String outcome) {
        meterRegistry.counter("map.route.cache.lease", "outcome", outcome).increment();
    }
}
