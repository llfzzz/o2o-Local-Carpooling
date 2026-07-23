package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
class RouteQuoteService {

    private final MapProviderSelector providerSelector;
    private final MapCityRegistry cityRegistry;
    private final RouteSnapshotRepository repository;
    private final MapProviderCircuitBreaker circuitBreaker;
    private final MapResilienceProperties resilienceProperties;
    private final MeterRegistry meterRegistry;
    private final RouteRedisCache routeRedisCache;
    private final RouteCacheLease routeCacheLease;
    private final ConcurrentHashMap<String, CompletableFuture<RouteSnapshot>> inFlight = new ConcurrentHashMap<>();

    RouteQuoteService(
        MapProviderSelector providerSelector,
        MapCityRegistry cityRegistry,
        RouteSnapshotRepository repository,
        MapProviderCircuitBreaker circuitBreaker,
        MapResilienceProperties resilienceProperties,
        MeterRegistry meterRegistry,
        RouteRedisCache routeRedisCache,
        RouteCacheLease routeCacheLease
    ) {
        this.providerSelector = providerSelector;
        this.cityRegistry = cityRegistry;
        this.repository = repository;
        this.circuitBreaker = circuitBreaker;
        this.resilienceProperties = resilienceProperties;
        this.meterRegistry = meterRegistry;
        this.routeRedisCache = routeRedisCache;
        this.routeCacheLease = routeCacheLease;
    }

    /** Legacy text form, retained for the older {@code GET /api/maps/route} contract. */
    RouteSnapshot quote(RouteQuoteRequest request) {
        validate(request);
        MapProvider provider = providerSelector.active();
        RouteQuoteResult result = circuitBreaker.execute(provider, "route", () -> provider.quote(request));
        repository.save(result);
        return result.routeSnapshot();
    }

    /**
     * Structured form. Both endpoints are already resolved, so the only work left is enforcing the
     * city allowlist and asking the provider for distance, duration and geometry.
     */
    RouteSnapshot quote(LocationRef origin, LocationRef destination) {
        cityRegistry.requireEnabled(origin.adcode());     // validate FIRST — city errors are never cached
        cityRegistry.requireEnabled(destination.adcode());
        MapProvider provider = providerSelector.active();
        RouteQuoteRequest request = RouteQuoteRequest.ofLocations(origin, destination);
        MapResilienceProperties.RouteCache cache = resilienceProperties.getRouteCache();
        String cacheKey = cache.isEnabled()
            ? RouteSnapshotRepository.cacheKey(provider.name(), origin, destination)
            : null;
        if (cacheKey == null) {
            return fetchAndSave(provider, request);
        }

        // 1. Redis fresh (fast path, no single-flight). Endpoints are re-applied per caller, so two
        // callers in the same ~100m grid with different POI names never leak endpoints to each other.
        Optional<RouteSnapshot> redisHit = routeRedisCache.get(cacheKey);
        if (redisHit.isPresent()) {
            recordCacheHit(provider, "redis");
            return withEndpoints(redisHit.get(), origin, destination);
        }

        // 2. In-process single-flight around the fill; the distributed lease adds cross-instance dedup.
        RouteSnapshot shared = coalesce(cacheKey, () -> fill(provider, request, cacheKey));
        return withEndpoints(shared, origin, destination);
    }

    private RouteSnapshot fill(MapProvider provider, RouteQuoteRequest request, String cacheKey) {
        Optional<String> lease = routeCacheLease.tryAcquire(cacheKey);
        if (lease.isPresent()) {
            try {
                // Another instance may have populated the cache between our miss and winning the lease.
                Optional<RouteSnapshot> redisHit = routeRedisCache.get(cacheKey);
                if (redisHit.isPresent()) {
                    recordCacheHit(provider, "redis");
                    return redisHit.get();
                }
                return authoritativeLoad(provider, request, cacheKey);
            } finally {
                routeCacheLease.release(cacheKey, lease.get());
            }
        }
        // Lease loser: wait a bounded time rechecking the cache, then fall back to a safe load anyway.
        // Duplicate provider work is acceptable if the wait expires; blocking indefinitely is not.
        Optional<RouteSnapshot> waited = waitForFill(provider, cacheKey);
        if (waited.isPresent()) {
            return waited.get();
        }
        meterRegistry.counter("map.route.cache.lease", "outcome", "timeout").increment();
        return authoritativeLoad(provider, request, cacheKey);
    }

    private Optional<RouteSnapshot> waitForFill(MapProvider provider, String cacheKey) {
        MapResilienceProperties.Redis redis = resilienceProperties.getRouteCache().getRedis();
        long deadline = System.nanoTime() + Math.max(0, redis.getLeaseWait().toNanos());
        long backoffMillis = Math.max(1, redis.getLeaseBackoff().toMillis());
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(backoffMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            Optional<RouteSnapshot> redisHit = routeRedisCache.get(cacheKey);
            if (redisHit.isPresent()) {
                recordCacheHit(provider, "redis");
                return redisHit;
            }
        }
        return Optional.empty();
    }

    /**
     * The authoritative fill: fresh MySQL snapshot else the provider (through the circuit breaker),
     * with the existing bounded stale fallback. Populates Redis only after an authoritative result is
     * available, and never caches (or lets Redis mask) credential/city/coordinate/bad-request errors.
     */
    private RouteSnapshot authoritativeLoad(MapProvider provider, RouteQuoteRequest request, String cacheKey) {
        MapResilienceProperties.RouteCache cache = resilienceProperties.getRouteCache();

        Optional<RouteSnapshotRepository.CachedRoute> fresh =
            repository.findLatestWithTimestamp(cacheKey, Instant.now().minus(cache.getFreshTtl()));
        if (fresh.isPresent()) {
            recordLoad("mysql");
            routeRedisCache.put(cacheKey, fresh.get().route(), fresh.get().capturedAt());
            return fresh.get().route();
        }

        Optional<RouteSnapshotRepository.CachedRoute> stale =
            repository.findLatestWithTimestamp(cacheKey, Instant.now().minus(cache.getStaleIfError()));
        try {
            RouteSnapshot fetched = fetchAndSave(provider, request);
            recordLoad("provider");
            routeRedisCache.put(cacheKey, fetched, Instant.now());
            return fetched;
        } catch (MapProviderConfigurationException | MapProviderRequestException | IllegalArgumentException exception) {
            throw exception; // missing credentials / bad request / invalid input — never cached, never masked
        } catch (RuntimeException exception) {
            if (!"demo".equalsIgnoreCase(provider.name()) && stale.isPresent()) {
                recordCacheHit(provider, "stale");
                return markStale(stale.get().route()); // do NOT populate Redis with stale data
            }
            throw unavailable(exception);
        }
    }

    private void recordLoad(String source) {
        meterRegistry.counter("map.route.redis.cache.loads", "source", source).increment();
    }

    private RouteSnapshot fetchAndSave(MapProvider provider, RouteQuoteRequest request) {
        RouteQuoteResult result = circuitBreaker.execute(provider, "route", () -> provider.quote(request));
        repository.save(result);
        return result.routeSnapshot();
    }

    private RouteSnapshot withEndpoints(RouteSnapshot cached, LocationRef origin, LocationRef destination) {
        return new RouteSnapshot(
            cached.routeId(),
            cached.distanceMeters(),
            cached.durationSeconds(),
            cached.providerTrace(),
            cached.polyline(),
            origin,
            destination
        );
    }

    private RouteSnapshot markStale(RouteSnapshot cached) {
        return new RouteSnapshot(
            cached.routeId(),
            cached.distanceMeters(),
            cached.durationSeconds(),
            cached.providerTrace() + "-stale-cache",
            cached.polyline(),
            cached.origin(),
            cached.destination()
        );
    }

    private RouteSnapshot coalesce(String cacheKey, java.util.function.Supplier<RouteSnapshot> loader) {
        CompletableFuture<RouteSnapshot> created = new CompletableFuture<>();
        CompletableFuture<RouteSnapshot> existing = inFlight.putIfAbsent(cacheKey, created);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException exception) {
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw exception;
            }
        }

        try {
            RouteSnapshot loaded = loader.get();
            created.complete(loaded);
            return loaded;
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(cacheKey, created);
        }
    }

    private void recordCacheHit(MapProvider provider, String freshness) {
        meterRegistry.counter(
            "map.route.cache.hits",
            "provider", provider.name(),
            "freshness", freshness
        ).increment();
    }

    private BusinessException unavailable(RuntimeException cause) {
        if (cause instanceof BusinessException businessException
            && "MAP_PROVIDER_UNAVAILABLE".equals(businessException.errorCode())) {
            return businessException;
        }
        BusinessException exception = new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "MAP_PROVIDER_UNAVAILABLE",
            "map provider is temporarily unavailable"
        );
        exception.initCause(cause);
        return exception;
    }

    private void validate(RouteQuoteRequest request) {
        if (!StringUtils.hasText(request.origin())) {
            throw new IllegalArgumentException("origin is required");
        }
        if (!StringUtils.hasText(request.destination())) {
            throw new IllegalArgumentException("destination is required");
        }
    }
}
