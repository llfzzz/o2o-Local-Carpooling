package com.o2o.carpooling.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the distributed limiter with real Redis: one shared quota across limiter instances, atomic
 * under concurrency, window reset, and a {@code Retry-After} taken from the true window remainder.
 * Skipped without a Docker daemon so {@code mvn test} still passes.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisReactiveRateLimiterIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void twoLimiterInstancesShareOneCombinedQuota() {
        GatewayRateLimiter a = new RedisReactiveRateLimiter(redis);
        GatewayRateLimiter b = new RedisReactiveRateLimiter(redis);
        Duration window = Duration.ofSeconds(60);

        assertThat(a.allow("gateway:api:user-1", 3, window).block().allowed()).isTrue();  // 1
        assertThat(b.allow("gateway:api:user-1", 3, window).block().allowed()).isTrue();  // 2
        assertThat(a.allow("gateway:api:user-1", 3, window).block().allowed()).isTrue();  // 3
        // The 4th request across the two instances exceeds the shared quota.
        assertThat(b.allow("gateway:api:user-1", 3, window).block().allowed()).isFalse(); // 4
    }

    @Test
    void concurrentRequestsNeverExceedTheConfiguredLimit() throws Exception {
        GatewayRateLimiter limiter = new RedisReactiveRateLimiter(redis);
        int threads = 12;
        int limit = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger allowed = new AtomicInteger();
        Future<?>[] futures = new Future<?>[threads];
        for (int i = 0; i < threads; i++) {
            futures[i] = pool.submit(() -> {
                barrier.await();
                if (limiter.allow("gateway:api:hot", limit, Duration.ofSeconds(60)).block().allowed()) {
                    allowed.incrementAndGet();
                }
                return null;
            });
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(allowed.get()).isEqualTo(limit); // atomic INCR — never over the limit
    }

    @Test
    void windowExpiryResetsTheQuota() throws Exception {
        GatewayRateLimiter limiter = new RedisReactiveRateLimiter(redis);
        Duration window = Duration.ofSeconds(1);

        assertThat(limiter.allow("gateway:auth:1.2.3.4", 1, window).block().allowed()).isTrue();
        assertThat(limiter.allow("gateway:auth:1.2.3.4", 1, window).block().allowed()).isFalse();
        Thread.sleep(1_200); // let the fixed window elapse
        assertThat(limiter.allow("gateway:auth:1.2.3.4", 1, window).block().allowed()).isTrue();
    }

    @Test
    void retryAfterReflectsTheWindowRemainder() {
        GatewayRateLimiter limiter = new RedisReactiveRateLimiter(redis);
        Duration window = Duration.ofSeconds(60);

        limiter.allow("gateway:auth:5.6.7.8", 1, window).block();
        RateLimitDecision rejected = limiter.allow("gateway:auth:5.6.7.8", 1, window).block();

        assertThat(rejected.allowed()).isFalse();
        // Retry-After is the real remaining window, not a fixed constant.
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void separateKeysHaveIndependentQuotas() {
        GatewayRateLimiter limiter = new RedisReactiveRateLimiter(redis);
        Duration window = Duration.ofSeconds(60);

        assertThat(limiter.allow("gateway:auth:1.1.1.1", 1, window).block().allowed()).isTrue();
        assertThat(limiter.allow("gateway:auth:1.1.1.1", 1, window).block().allowed()).isFalse();
        // A different client IP is unaffected.
        assertThat(limiter.allow("gateway:auth:2.2.2.2", 1, window).block().allowed()).isTrue();
    }
}
