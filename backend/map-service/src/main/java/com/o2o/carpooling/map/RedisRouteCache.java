package com.o2o.carpooling.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * Cache-redis-backed implementation. Keys are {@code cache:map:route:v1:<sha256(normalized-key)>} —
 * schema-versioned, bounded length, and containing no coordinates/credentials/user data. Values are a
 * versioned JSON String so each entry has an independent TTL and can be atomically replaced.
 */
class RedisRouteCache implements RouteRedisCache {

    private static final String KEY_PREFIX = "cache:map:route:v1:";
    private static final int SCHEMA_VERSION = 1;

    private final StringRedisTemplate redis;
    private final MapResilienceProperties resilience;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DoubleSupplier jitterRandom; // [0,1) — seam so tests can pin the jitter

    RedisRouteCache(StringRedisTemplate redis, MapResilienceProperties resilience, MeterRegistry meterRegistry) {
        this(redis, resilience, meterRegistry, () -> ThreadLocalRandom.current().nextDouble());
    }

    RedisRouteCache(StringRedisTemplate redis, MapResilienceProperties resilience, MeterRegistry meterRegistry,
                    DoubleSupplier jitterRandom) {
        this.redis = redis;
        this.resilience = resilience;
        this.meterRegistry = meterRegistry;
        this.jitterRandom = jitterRandom;
    }

    @Override
    public Optional<RouteSnapshot> get(String cacheKey) {
        if (cacheKey == null) {
            return Optional.empty();
        }
        String redisKey = redisKey(cacheKey);
        String raw;
        try {
            raw = redis.opsForValue().get(redisKey);
        } catch (RuntimeException redisError) {
            requests("error"); // Redis down/slow -> miss, fall through to MySQL/provider
            return Optional.empty();
        }
        if (raw == null) {
            requests("miss");
            return Optional.empty();
        }
        try {
            CachedRouteValue value = objectMapper.readValue(raw, CachedRouteValue.class);
            if (value.v() != SCHEMA_VERSION || value.routeId() == null) {
                return decodeError(redisKey); // old schema / corrupt -> repair + miss
            }
            requests("hit");
            return Optional.of(new RouteSnapshot(
                value.routeId(), value.distanceMeters(), value.durationSeconds(),
                value.providerTrace(), value.polyline(), null, null));
        } catch (Exception decodeFailure) {
            return decodeError(redisKey);
        }
    }

    @Override
    public void put(String cacheKey, RouteSnapshot routeCore, Instant capturedAt) {
        if (cacheKey == null || routeCore == null || capturedAt == null) {
            return;
        }
        MapResilienceProperties.Redis cfg = resilience.getRouteCache().getRedis();
        Duration ttl = ttlFor(capturedAt, cfg);
        if (ttl.isZero() || ttl.isNegative()) {
            populate("skipped"); // source is at/near the stale boundary — not worth caching
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new CachedRouteValue(
                SCHEMA_VERSION, routeCore.routeId(), routeCore.distanceMeters(), routeCore.durationSeconds(),
                routeCore.providerTrace(), routeCore.polyline(), capturedAt.toEpochMilli()));
        } catch (Exception serialize) {
            populate("error");
            return;
        }
        if (payload.getBytes(StandardCharsets.UTF_8).length > cfg.getMaxPayloadBytes()) {
            populate("oversized"); // big-key protection
            return;
        }
        try {
            redis.opsForValue().set(redisKey(cacheKey), payload, ttl.toMillis(), TimeUnit.MILLISECONDS);
            populate("success");
        } catch (RuntimeException redisError) {
            populate("error"); // MySQL already holds the authoritative row — cache write is best-effort
        }
    }

    /**
     * base + random(0, base*jitter), then capped by the snapshot's remaining freshness so an
     * almost-expired MySQL snapshot never receives a brand-new full Redis TTL. Package-visible + pure
     * for testing.
     */
    Duration ttlFor(Instant capturedAt, MapResilienceProperties.Redis cfg) {
        long baseMillis = cfg.getBaseTtl().toMillis();
        double jitterFraction = Math.max(0, cfg.getTtlJitter());
        long jittered = baseMillis + (long) (baseMillis * jitterFraction * clamp01(jitterRandom.getAsDouble()));
        long remainingFreshMillis = resilience.getRouteCache().getFreshTtl()
            .minus(Duration.between(capturedAt, Instant.now())).toMillis();
        return Duration.ofMillis(Math.min(jittered, remainingFreshMillis));
    }

    private Optional<RouteSnapshot> decodeError(String redisKey) {
        requests("decode_error");
        try {
            redis.delete(redisKey); // repair the poisoned entry so the next caller reloads cleanly
        } catch (RuntimeException ignored) {
            // best-effort repair
        }
        return Optional.empty();
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        return v >= 1 ? Math.nextDown(1.0) : v;
    }

    private String redisKey(String cacheKey) {
        return redisKeyFor(cacheKey);
    }

    /** The concrete Redis key for a normalized cache key: {@code cache:map:route:v1:<sha256>}. */
    static String redisKeyFor(String cacheKey) {
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

    private void requests(String outcome) {
        meterRegistry.counter("map.route.redis.cache.requests", "outcome", outcome).increment();
    }

    private void populate(String outcome) {
        meterRegistry.counter("map.route.redis.cache.populate", "outcome", outcome).increment();
    }

    /** Versioned, deterministic cache payload (record component order is stable). No endpoints/PII. */
    record CachedRouteValue(int v, String routeId, int distanceMeters, int durationSeconds,
                            String providerTrace, String polyline, long capturedAtEpochMillis) {
    }
}
