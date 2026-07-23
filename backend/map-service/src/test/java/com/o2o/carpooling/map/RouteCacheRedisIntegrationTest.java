package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Redis (Lua / NX / cross-instance) behavior that mocks can't prove. Skipped when no Docker
 * daemon is available so {@code mvn test} still passes on such hosts.
 */
@Testcontainers(disabledWithoutDocker = true)
class RouteCacheRedisIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private MapResilienceProperties resilience;
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        resilience = new MapResilienceProperties();
        resilience.getRouteCache().setFreshTtl(Duration.ofMinutes(30));
        MapResilienceProperties.Redis cfg = resilience.getRouteCache().getRedis();
        cfg.setEnabled(true);
        cfg.setBaseTtl(Duration.ofMinutes(5));
        cfg.setLeaseTtl(Duration.ofSeconds(10));
        cfg.setLeaseWait(Duration.ofSeconds(3));
        cfg.setLeaseBackoff(Duration.ofMillis(40));
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void putThenGetReturnsTheCachedRouteCore() {
        RedisRouteCache cache = new RedisRouteCache(redis, resilience, registry);
        cache.put("amap:k1", core("route-1"), Instant.now());

        Optional<RouteSnapshot> hit = cache.get("amap:k1");

        assertThat(hit).isPresent();
        assertThat(hit.get().routeId()).isEqualTo("route-1");
        assertThat(hit.get().distanceMeters()).isEqualTo(1_000);
        // endpoints are never cached — they are re-applied per request
        assertThat(hit.get().origin()).isNull();
        assertThat(hit.get().destination()).isNull();
    }

    @Test
    void malformedCachedValueIsIgnoredAndRepaired() {
        redis.opsForValue().set(RedisRouteCache.redisKeyFor("amap:bad"), "not-json{");
        RedisRouteCache cache = new RedisRouteCache(redis, resilience, registry);

        assertThat(cache.get("amap:bad")).isEmpty();
        assertThat(redis.hasKey(RedisRouteCache.redisKeyFor("amap:bad"))).isFalse(); // poisoned entry repaired
    }

    @Test
    void oversizedValueIsNotCached() {
        resilience.getRouteCache().getRedis().setMaxPayloadBytes(1);
        RedisRouteCache cache = new RedisRouteCache(redis, resilience, registry);

        cache.put("amap:big", core("route-big"), Instant.now());

        assertThat(cache.get("amap:big")).isEmpty();
    }

    @Test
    void leaseIsExclusiveAndReleasesOnlyForTheOwner() {
        RedisRouteCacheLease lease = new RedisRouteCacheLease(redis, resilience, registry);

        Optional<String> owner = lease.tryAcquire("amap:lease");
        assertThat(owner).isPresent();
        assertThat(lease.tryAcquire("amap:lease")).isEmpty(); // held

        lease.release("amap:lease", "someone-else"); // wrong token must not release
        assertThat(lease.tryAcquire("amap:lease")).isEmpty();

        lease.release("amap:lease", owner.get()); // owner token releases
        assertThat(lease.tryAcquire("amap:lease")).isPresent();
    }

    @Test
    void expiredLeaseCanBeReacquired() throws Exception {
        resilience.getRouteCache().getRedis().setLeaseTtl(Duration.ofMillis(150));
        RedisRouteCacheLease lease = new RedisRouteCacheLease(redis, resilience, registry);

        assertThat(lease.tryAcquire("amap:expiry")).isPresent();
        Thread.sleep(300); // let the lease PX expire
        assertThat(lease.tryAcquire("amap:expiry")).isPresent();
    }

    @Test
    void concurrentMissesAcrossTwoInstancesCallTheProviderOnce() throws Exception {
        CountingProvider provider = new CountingProvider(200);
        RouteQuoteService instanceA = instance(provider);
        RouteQuoteService instanceB = instance(provider);
        LocationRef origin = place("350211", "软件园三期");
        LocationRef destination = place("350211", "集美大学");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<RouteSnapshot> a = pool.submit(() -> {
            barrier.await();
            return instanceA.quote(origin, destination);
        });
        Future<RouteSnapshot> b = pool.submit(() -> {
            barrier.await();
            return instanceB.quote(origin, destination);
        });
        RouteSnapshot routeA = a.get();
        RouteSnapshot routeB = b.get();
        pool.shutdown();

        // The distributed lease collapses the two independent-instance misses into one provider call.
        assertThat(provider.calls()).isEqualTo(1);
        assertThat(routeA.routeId()).isEqualTo("route-live");
        assertThat(routeB.routeId()).isEqualTo("route-live");
        // Each caller kept its own request endpoints despite sharing the cached core.
        assertThat(routeA.origin()).isEqualTo(origin);
        assertThat(routeB.destination()).isEqualTo(destination);
    }

    private RouteQuoteService instance(CountingProvider provider) {
        ProviderProperties providerProperties = new ProviderProperties();
        providerProperties.getMap().setType("amap");
        return new RouteQuoteService(
            new MapProviderSelector(List.of(provider), providerProperties),
            new MapCityRegistry(),
            new NoRouteRepository(),
            new MapProviderCircuitBreaker(resilience, registry),
            resilience,
            registry,
            new RedisRouteCache(redis, resilience, registry),
            new RedisRouteCacheLease(redis, resilience, registry)
        );
    }

    private static RouteSnapshot core(String routeId) {
        return new RouteSnapshot(routeId, 1_000, 120, "amap-v5", "118.17,24.48;118.09,24.57", null, null);
    }

    private static LocationRef place(String adcode, String name) {
        return new LocationRef(GeoPoint.gcj02(24.4879, 118.1781), "amap", null, "0592", adcode, name, name,
            LocationSource.POI_SEARCH, null, Instant.now());
    }

    /** MySQL never has a snapshot here, so every fill goes to the provider (guarded by the lease). */
    private static final class NoRouteRepository extends RouteSnapshotRepository {
        NoRouteRepository() {
            super(null);
        }

        @Override
        void save(RouteQuoteResult result) {
            // no-op — the cross-instance dedup here is the distributed lease, not MySQL
        }

        @Override
        Optional<CachedRoute> findLatestWithTimestamp(String cacheKey, Instant notBefore) {
            return Optional.empty();
        }
    }

    private static final class CountingProvider implements MapProvider {
        private final long delayMillis;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingProvider(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        int calls() {
            return calls.get();
        }

        @Override
        public String name() {
            return "amap";
        }

        @Override
        public RouteQuoteResult quote(RouteQuoteRequest request) {
            calls.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", exception);
                }
            }
            RouteSnapshot route = new RouteSnapshot("route-live", 22_500, 2_600, "amap-v5",
                "118.1781,24.4879;118.0972,24.5751", request.originRef(), request.destinationRef());
            return RouteQuoteResult.from(request, route, "amap", null, null, "{}");
        }

        @Override
        public LocationRef reverseGeocode(GeoPoint point) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LocationRef> suggest(PlaceQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LocationRef> searchPoi(PlaceQuery query) {
            throw new UnsupportedOperationException();
        }
    }
}
