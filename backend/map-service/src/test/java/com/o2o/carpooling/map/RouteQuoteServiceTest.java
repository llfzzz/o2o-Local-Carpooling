package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteQuoteServiceTest {

    @Test
    void usesMockProviderWhenAmapKeyIsBlankAndPersistsSnapshot() {
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = new RouteQuoteService(
            new AmapProperties(),
            new StubProvider(false, new RouteSnapshot("route-real", 22_000, 3_000, "amap-v5")),
            new StubProvider(true, new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock")),
            repository
        );

        RouteSnapshot route = service.quote(new RouteQuoteRequest("软件园三期", "集美大学", "厦门"));

        assertThat(route.providerTrace()).isEqualTo("amap-mock");
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().provider()).isEqualTo("amap-mock");
        assertThat(repository.saved.getFirst().providerResponseSnapshot()).doesNotContain("AMAP_API_KEY");
    }

    @Test
    void doesNotFallbackToMockWhenConfiguredAmapProviderFails() {
        AmapProperties properties = new AmapProperties();
        properties.setApiKey("secret-amap-key");
        CapturingRouteSnapshotRepository repository = new CapturingRouteSnapshotRepository();
        RouteQuoteService service = new RouteQuoteService(
            properties,
            request -> {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "MAP_ROUTE_QUOTE_FAILED", "amap route quote failed");
            },
            new StubProvider(true, new RouteSnapshot("route-mock", 18_500, 2_312, "amap-mock")),
            repository
        );

        assertThatThrownBy(() -> service.quote(new RouteQuoteRequest("软件园三期", "集美大学", "厦门")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("amap route quote failed");
        assertThat(repository.saved).isEmpty();
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

    private record StubProvider(boolean supported, RouteSnapshot route) implements MapRouteProvider {
        @Override
        public boolean supports() {
            return supported;
        }

        @Override
        public RouteQuoteResult quote(RouteQuoteRequest request) {
            return RouteQuoteResult.from(request, route, route.providerTrace(), null, null, "mock-route-snapshot");
        }
    }
}
