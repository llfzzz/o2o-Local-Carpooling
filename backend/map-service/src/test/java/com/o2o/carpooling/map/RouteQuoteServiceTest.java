package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteQuoteServiceTest {

    @Test
    void selectsProviderByConfiguredTypeAndPersistsSnapshot() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = new RouteQuoteService(
            List.of(
                new StubProvider("amap", new RouteSnapshot("route-real", 22_000, 3_000, "amap-v5"), false),
                new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false)
            ),
            providers("demo"),
            repository
        );

        RouteSnapshot route = service.quote(new RouteQuoteRequest("软件园三期", "集美大学", "厦门"));

        assertThat(route.providerTrace()).isEqualTo("amap-mock");
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().provider()).isEqualTo("amap-mock");
    }

    @Test
    void doesNotFallbackToMockWhenConfiguredProviderFails() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = new RouteQuoteService(
            List.of(
                new StubProvider("amap", new RouteSnapshot("route-real", 22_000, 3_000, "amap-v5"), true),
                new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false)
            ),
            providers("amap"),
            repository
        );

        assertThatThrownBy(() -> service.quote(new RouteQuoteRequest("软件园三期", "集美大学", "厦门")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("amap route quote failed");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void failsClosedWhenMapProviderTypeUnconfigured() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = new RouteQuoteService(
            List.of(new StubProvider("demo", new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock"), false)),
            providers(""),
            repository
        );

        assertThatThrownBy(() -> service.quote(new RouteQuoteRequest("软件园三期", "集美大学", "厦门")))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("MAP_PROVIDER_UNCONFIGURED"));
        assertThat(repository.saved).isEmpty();
    }

    private ProviderProperties providers(String mapType) {
        ProviderProperties properties = new ProviderProperties();
        properties.getMap().setType(mapType);
        return properties;
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

    private record StubProvider(String providerName, RouteSnapshot route, boolean fail) implements MapRouteProvider {
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
    }
}
