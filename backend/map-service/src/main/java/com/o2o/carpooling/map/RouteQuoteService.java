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
    private final ConcurrentHashMap<String, CompletableFuture<RouteSnapshot>> inFlight = new ConcurrentHashMap<>();

    RouteQuoteService(
        MapProviderSelector providerSelector,
        MapCityRegistry cityRegistry,
        RouteSnapshotRepository repository,
        MapProviderCircuitBreaker circuitBreaker,
        MapResilienceProperties resilienceProperties,
        MeterRegistry meterRegistry
    ) {
        this.providerSelector = providerSelector;
        this.cityRegistry = cityRegistry;
        this.repository = repository;
        this.circuitBreaker = circuitBreaker;
        this.resilienceProperties = resilienceProperties;
        this.meterRegistry = meterRegistry;
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
        cityRegistry.requireEnabled(origin.adcode());
        cityRegistry.requireEnabled(destination.adcode());
        MapProvider provider = providerSelector.active();
        RouteQuoteRequest request = RouteQuoteRequest.ofLocations(origin, destination);
        MapResilienceProperties.RouteCache cache = resilienceProperties.getRouteCache();
        String cacheKey = cache.isEnabled()
            ? RouteSnapshotRepository.cacheKey(provider.name(), origin, destination)
            : null;

        Optional<RouteSnapshot> fresh = findCached(cacheKey, cache.getFreshTtl());
        if (fresh.isPresent()) {
            recordCacheHit(provider, "fresh");
            return withEndpoints(fresh.get(), origin, destination);
        }
        if (cacheKey == null) {
            return fetchAndSave(provider, request);
        }
        RouteSnapshot shared = coalesce(cacheKey, () -> loadStructured(provider, request, cacheKey));
        // Single-flight shares only the expensive route core. Callers in the same ~100m grid can
        // legitimately carry different POI names/sources, so every response must keep its own
        // request endpoints.
        return withEndpoints(shared, origin, destination);
    }

    private RouteSnapshot loadStructured(
        MapProvider provider,
        RouteQuoteRequest request,
        String cacheKey
    ) {
        MapResilienceProperties.RouteCache cache = resilienceProperties.getRouteCache();
        Optional<RouteSnapshot> fresh = findCached(cacheKey, cache.getFreshTtl());
        if (fresh.isPresent()) {
            recordCacheHit(provider, "fresh");
            return fresh.get();
        }

        Optional<RouteSnapshot> stale = findCached(cacheKey, cache.getStaleIfError());
        try {
            return fetchAndSave(provider, request);
        } catch (MapProviderConfigurationException | MapProviderRequestException | IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (!"demo".equalsIgnoreCase(provider.name()) && stale.isPresent()) {
                recordCacheHit(provider, "stale");
                return markStale(stale.get());
            }
            throw unavailable(exception);
        }
    }

    private RouteSnapshot fetchAndSave(MapProvider provider, RouteQuoteRequest request) {
        RouteQuoteResult result = circuitBreaker.execute(provider, "route", () -> provider.quote(request));
        repository.save(result);
        return result.routeSnapshot();
    }

    private Optional<RouteSnapshot> findCached(String cacheKey, java.time.Duration ttl) {
        if (cacheKey == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return Optional.empty();
        }
        return repository.findLatest(cacheKey, Instant.now().minus(ttl));
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
