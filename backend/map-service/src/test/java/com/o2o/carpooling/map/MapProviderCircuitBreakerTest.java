package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.foundation.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapProviderCircuitBreakerTest {

    @Test
    void opensAfterConfiguredFailureThresholdAndRejectsWithoutCallingProvider() {
        MapResilienceProperties properties = properties(2, 2, Duration.ofSeconds(30));
        MapProviderCircuitBreaker breaker = new MapProviderCircuitBreaker(
            properties,
            new SimpleMeterRegistry()
        );
        FailingProvider provider = new FailingProvider();

        assertThatThrownBy(() -> breaker.execute(provider, "route", provider::fail))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> breaker.execute(provider, "route", provider::fail))
            .isInstanceOf(BusinessException.class);

        assertThat(breaker.state("amap")).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breaker.execute(provider, "route", provider::fail))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo("MAP_PROVIDER_UNAVAILABLE"));
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void halfOpenProbeClosesTheCircuitAfterTheWaitDuration() throws Exception {
        MapResilienceProperties properties = properties(2, 2, Duration.ofMillis(10));
        properties.getCircuitBreaker().setPermittedCallsInHalfOpenState(1);
        MapProviderCircuitBreaker breaker = new MapProviderCircuitBreaker(
            properties,
            new SimpleMeterRegistry()
        );
        FailingProvider provider = new FailingProvider();

        assertThatThrownBy(() -> breaker.execute(provider, "route", provider::fail));
        assertThatThrownBy(() -> breaker.execute(provider, "route", provider::fail));
        Thread.sleep(30);

        assertThat(breaker.execute(provider, "route", () -> "ok")).isEqualTo("ok");
        assertThat(breaker.state("amap")).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private MapResilienceProperties properties(int window, int minimumCalls, Duration wait) {
        MapResilienceProperties properties = new MapResilienceProperties();
        properties.getCircuitBreaker().setSlidingWindowSize(window);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(minimumCalls);
        properties.getCircuitBreaker().setWaitDurationInOpenState(wait);
        return properties;
    }

    private static final class FailingProvider implements MapProvider {
        private int calls;

        String fail() {
            calls++;
            throw new IllegalStateException("provider down");
        }

        @Override
        public String name() {
            return "amap";
        }

        @Override
        public RouteQuoteResult quote(RouteQuoteRequest request) {
            throw new UnsupportedOperationException();
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
