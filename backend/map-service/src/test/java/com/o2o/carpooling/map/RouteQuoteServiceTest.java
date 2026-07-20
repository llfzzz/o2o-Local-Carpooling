package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteQuoteServiceTest {

    @Test
    void selectsProviderByConfiguredTypeAndPersistsSnapshot() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = service("demo", repository, unrestrictedCities(),
            new StubProvider("amap", new RouteSnapshot("route-real", 22_000, 3_000, "amap-v5"), false),
            new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false));

        RouteSnapshot route = service.quote(RouteQuoteRequest.ofText("软件园三期", "集美大学", "厦门"));

        assertThat(route.providerTrace()).isEqualTo("amap-mock");
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().provider()).isEqualTo("amap-mock");
    }

    @Test
    void doesNotFallbackToMockWhenConfiguredProviderFails() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = service("amap", repository, unrestrictedCities(),
            new StubProvider("amap", new RouteSnapshot("route-real", 22_000, 3_000, "amap-v5"), true),
            new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false));

        assertThatThrownBy(() -> service.quote(RouteQuoteRequest.ofText("软件园三期", "集美大学", "厦门")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("amap route quote failed");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void failsClosedWhenMapProviderTypeUnconfigured() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = service("", repository, unrestrictedCities(),
            new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false));

        assertThatThrownBy(() -> service.quote(RouteQuoteRequest.ofText("软件园三期", "集美大学", "厦门")))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("MAP_PROVIDER_UNCONFIGURED"));
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void structuredQuoteRejectsLocationsOutsideTheSupportedCityAllowlist() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = service("demo", repository, citiesEnabling("3502"),
            new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false));

        // 成都 (510105) is not on the allowlist, so the quote must be refused rather than served.
        assertThatThrownBy(() -> service.quote(place("350211", "软件园三期"), place("510105", "天府广场")))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("MAP_CITY_NOT_SUPPORTED"));
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void structuredQuoteAcceptsAnyCityWhenNoAllowlistIsConfigured() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = service("demo", repository, unrestrictedCities(),
            new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false));

        RouteSnapshot route = service.quote(place("230103", "哈尔滨西站"), place("110105", "望京SOHO"));

        assertThat(route.providerTrace()).isEqualTo("amap-mock");
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void structuredQuoteUsesFreshCacheWithoutCallingOrSavingTheProvider() {
        CachingRouteSnapshotRepository repository = new CachingRouteSnapshotRepository(
            cachedRoute("route-cached"),
            Instant.now().minus(Duration.ofMinutes(5))
        );
        CountingProvider provider = new CountingProvider("amap", false, 0);
        RouteQuoteService service = service("amap", repository, unrestrictedCities(), provider);

        LocationRef origin = place("350211", "软件园三期");
        LocationRef destination = place("350211", "集美大学");
        RouteSnapshot route = service.quote(origin, destination);

        assertThat(route.routeId()).isEqualTo("route-cached");
        assertThat(route.origin()).isSameAs(origin);
        assertThat(route.destination()).isSameAs(destination);
        assertThat(provider.calls()).isZero();
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void transientProviderFailureUsesBoundedStaleRealRoute() {
        CachingRouteSnapshotRepository repository = new CachingRouteSnapshotRepository(
            cachedRoute("route-stale"),
            Instant.now().minus(Duration.ofHours(2))
        );
        CountingProvider provider = new CountingProvider("amap", true, 0);
        RouteQuoteService service = service("amap", repository, unrestrictedCities(), provider);

        RouteSnapshot route = service.quote(
            place("350211", "软件园三期"),
            place("350211", "集美大学")
        );

        assertThat(route.routeId()).isEqualTo("route-stale");
        assertThat(route.providerTrace()).isEqualTo("amap-v5-stale-cache");
        assertThat(provider.calls()).isEqualTo(1);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void routeOlderThanStaleWindowDoesNotHideProviderFailure() {
        CachingRouteSnapshotRepository repository = new CachingRouteSnapshotRepository(
            cachedRoute("route-too-old"),
            Instant.now().minus(Duration.ofHours(25))
        );
        CountingProvider provider = new CountingProvider("amap", true, 0);
        RouteQuoteService service = service("amap", repository, unrestrictedCities(), provider);

        assertThatThrownBy(() -> service.quote(
            place("350211", "软件园三期"),
            place("350211", "集美大学")
        ))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo("MAP_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void staleCacheNeverMasksMissingProviderCredentials() {
        CachingRouteSnapshotRepository repository = new CachingRouteSnapshotRepository(
            cachedRoute("route-stale"),
            Instant.now().minus(Duration.ofHours(2))
        );
        MapProvider provider = new ConfigurationFailureProvider();
        RouteQuoteService service = service("amap", repository, unrestrictedCities(), provider);

        assertThatThrownBy(() -> service.quote(
            place("350211", "软件园三期"),
            place("350211", "集美大学")
        ))
            .isInstanceOf(MapProviderConfigurationException.class)
            .hasMessageContaining("AMAP_API_KEY");
    }

    @Test
    void staleCacheNeverMasksAnInvalidProviderRequest() {
        CachingRouteSnapshotRepository repository = new CachingRouteSnapshotRepository(
            cachedRoute("route-stale"),
            Instant.now().minus(Duration.ofHours(2))
        );
        MapProvider provider = new RequestFailureProvider();
        RouteQuoteService service = service("amap", repository, unrestrictedCities(), provider);

        assertThatThrownBy(() -> service.quote(
            place("350211", "软件园三期"),
            place("350211", "集美大学")
        ))
            .isInstanceOf(MapProviderRequestException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo("MAP_REQUEST_INVALID"));
    }

    @Test
    void concurrentStructuredMissesShareOneProviderCall() throws Exception {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        CountingProvider provider = new CountingProvider("amap", false, 150);
        RouteQuoteService service = service("amap", repository, unrestrictedCities(), provider);
        LocationRef origin = place("350211", "软件园三期");
        int callers = 8;
        CyclicBarrier barrier = new CyclicBarrier(callers);

        try (var executor = Executors.newFixedThreadPool(callers)) {
            var futures = java.util.stream.IntStream.range(0, callers)
                .mapToObj(index -> executor.submit(() -> {
                    barrier.await();
                    return service.quote(origin, place("350211", "集美大学-" + index));
                }))
                .toList();

            for (int index = 0; index < futures.size(); index++) {
                RouteSnapshot route = futures.get(index).get(3, TimeUnit.SECONDS);
                assertThat(route.routeId()).isEqualTo("route-live");
                assertThat(route.destination().displayName()).isEqualTo("集美大学-" + index);
            }
        }

        assertThat(provider.calls()).isEqualTo(1);
        assertThat(repository.saved).hasSize(1);
    }

    private RouteQuoteService service(
        String mapType,
        RouteSnapshotRepository repository,
        MapCityRegistry cityRegistry,
        MapProvider... providers
    ) {
        MapResilienceProperties resilience = new MapResilienceProperties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new RouteQuoteService(
            new MapProviderSelector(List.of(providers), providerProperties(mapType)),
            cityRegistry,
            repository,
            new MapProviderCircuitBreaker(resilience, registry),
            resilience,
            registry
        );
    }

    private ProviderProperties providerProperties(String mapType) {
        ProviderProperties properties = new ProviderProperties();
        properties.getMap().setType(mapType);
        return properties;
    }

    private MapCityRegistry unrestrictedCities() {
        return new MapCityRegistry();
    }

    private MapCityRegistry citiesEnabling(String adcodePrefix) {
        MapCityRegistry registry = new MapCityRegistry();
        MapCityRegistry.SupportedCity city = new MapCityRegistry.SupportedCity();
        city.setAdcodePrefix(adcodePrefix);
        city.setName("厦门");
        city.setCityCode("0592");
        registry.setEnabled(new ArrayList<>(List.of(city)));
        return registry;
    }

    private static LocationRef place(String adcode, String name) {
        return new LocationRef(
            GeoPoint.gcj02(24.4879, 118.1781), "demo", null, "0592", adcode, name, name,
            LocationSource.DEMO_SEED, null, Instant.now());
    }

    private static RouteSnapshot cachedRoute(String routeId) {
        return new RouteSnapshot(
            routeId,
            22_500,
            2_600,
            "amap-v5",
            "118.1781,24.4879;118.0972,24.5751",
            null,
            null
        );
    }

    private static class CapturingRouteSnapshotRepository extends RouteSnapshotRepository {
        final List<RouteQuoteResult> saved = new ArrayList<>();

        CapturingRouteSnapshotRepository() {
            super(null);
        }

        @Override
        void save(RouteQuoteResult result) {
            saved.add(result);
        }

        @Override
        Optional<RouteSnapshot> findLatest(String cacheKey, Instant notBefore) {
            return Optional.empty();
        }
    }

    private static final class CachingRouteSnapshotRepository extends CapturingRouteSnapshotRepository {
        private final RouteSnapshot cached;
        private final Instant cachedAt;

        private CachingRouteSnapshotRepository(RouteSnapshot cached, Instant cachedAt) {
            this.cached = cached;
            this.cachedAt = cachedAt;
        }

        @Override
        Optional<RouteSnapshot> findLatest(String cacheKey, Instant notBefore) {
            return cachedAt.isBefore(notBefore) ? Optional.empty() : Optional.of(cached);
        }
    }

    private record StubProvider(String providerName, RouteSnapshot route, boolean fail) implements MapProvider {
        @Override
        public String name() {
            return providerName;
        }

        @Override
        public RouteQuoteResult quote(RouteQuoteRequest request) {
            if (fail) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "MAP_ROUTE_QUOTE_FAILED", "amap route quote failed");
            }
            return RouteQuoteResult.from(request, route, route.providerTrace(), null, null, "mock-route-snapshot");
        }

        @Override
        public LocationRef reverseGeocode(GeoPoint point) {
            throw new UnsupportedOperationException("not needed for route quote tests");
        }

        @Override
        public List<LocationRef> suggest(PlaceQuery query) {
            throw new UnsupportedOperationException("not needed for route quote tests");
        }

        @Override
        public List<LocationRef> searchPoi(PlaceQuery query) {
            throw new UnsupportedOperationException("not needed for route quote tests");
        }
    }

    private static final class CountingProvider implements MapProvider {
        private final String providerName;
        private final boolean fail;
        private final long delayMillis;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingProvider(String providerName, boolean fail, long delayMillis) {
            this.providerName = providerName;
            this.fail = fail;
            this.delayMillis = delayMillis;
        }

        int calls() {
            return calls.get();
        }

        @Override
        public String name() {
            return providerName;
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
            if (fail) {
                throw new BusinessException(
                    HttpStatus.BAD_GATEWAY,
                    "MAP_ROUTE_QUOTE_FAILED",
                    "amap route quote failed"
                );
            }
            LocationRef origin = request.originRef();
            LocationRef destination = request.destinationRef();
            RouteSnapshot route = new RouteSnapshot(
                "route-live",
                22_500,
                2_600,
                "amap-v5",
                "118.1781,24.4879;118.0972,24.5751",
                origin,
                destination
            );
            return RouteQuoteResult.from(request, route, providerName, null, null, "{}");
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

    private static final class ConfigurationFailureProvider implements MapProvider {
        @Override
        public String name() {
            return "amap";
        }

        @Override
        public RouteQuoteResult quote(RouteQuoteRequest request) {
            throw new MapProviderConfigurationException("AMAP_API_KEY is not configured");
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

    private static final class RequestFailureProvider implements MapProvider {
        @Override
        public String name() {
            return "amap";
        }

        @Override
        public RouteQuoteResult quote(RouteQuoteRequest request) {
            throw new MapProviderRequestException("amap rejected invalid route parameters");
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
