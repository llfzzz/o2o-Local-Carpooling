package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    private RouteQuoteService service(
        String mapType,
        RouteSnapshotRepository repository,
        MapCityRegistry cityRegistry,
        MapProvider... providers
    ) {
        return new RouteQuoteService(
            new MapProviderSelector(List.of(providers), providerProperties(mapType)),
            cityRegistry,
            repository
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

    private static final class CapturingRouteSnapshotRepository extends RouteSnapshotRepository {
        private final List<RouteQuoteResult> saved = new ArrayList<>();

        CapturingRouteSnapshotRepository() {
            super(null);
        }

        @Override
        void save(RouteQuoteResult result) {
            saved.add(result);
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
}
