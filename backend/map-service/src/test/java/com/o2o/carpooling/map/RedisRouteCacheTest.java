package com.o2o.carpooling.map;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Pure unit coverage of the TTL policy — jitter bounds and the remaining-freshness cap. */
class RedisRouteCacheTest {

    private final MapResilienceProperties resilience = new MapResilienceProperties();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void ttlJitterStaysWithinConfiguredBounds() {
        resilience.getRouteCache().setFreshTtl(Duration.ofMinutes(30));
        MapResilienceProperties.Redis cfg = resilience.getRouteCache().getRedis();
        cfg.setBaseTtl(Duration.ofMinutes(5));
        cfg.setTtlJitter(0.2);

        // random=0 -> exactly base; random->1 -> up to base*(1+jitter). Snapshot just captured, so the
        // remaining-freshness cap (~30m) does not bite here.
        Duration low = cache(() -> 0.0).ttlFor(Instant.now(), cfg);
        Duration high = cache(() -> 0.999).ttlFor(Instant.now(), cfg);

        assertThat(low.toMillis()).isBetween(Duration.ofMinutes(5).toMillis() - 200, Duration.ofMinutes(5).toMillis());
        assertThat(high.toMillis()).isBetween(Duration.ofMinutes(5).toMillis(), Duration.ofMinutes(6).toMillis());
    }

    @Test
    void oldMysqlSnapshotDoesNotReceiveAFullFreshRedisTtl() {
        resilience.getRouteCache().setFreshTtl(Duration.ofMinutes(30));
        MapResilienceProperties.Redis cfg = resilience.getRouteCache().getRedis();
        cfg.setBaseTtl(Duration.ofMinutes(10));
        cfg.setTtlJitter(0.0);

        // Captured 28 minutes ago: only ~2 min of the 30-min fresh window remains, far below the 10m base.
        Duration ttl = cache(() -> 0.0).ttlFor(Instant.now().minus(Duration.ofMinutes(28)), cfg);

        assertThat(ttl).isGreaterThan(Duration.ZERO).isLessThanOrEqualTo(Duration.ofMinutes(2));
    }

    private RedisRouteCache cache(DoubleSupplier jitter) {
        return new RedisRouteCache(mock(StringRedisTemplate.class), resilience, registry, jitter);
    }
}
