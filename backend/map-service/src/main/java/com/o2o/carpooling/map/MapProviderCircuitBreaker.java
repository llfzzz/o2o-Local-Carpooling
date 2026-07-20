package com.o2o.carpooling.map;

import com.o2o.carpooling.common.foundation.BusinessException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
class MapProviderCircuitBreaker {

    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;
    private final MeterRegistry meterRegistry;

    MapProviderCircuitBreaker(MapResilienceProperties properties, MeterRegistry meterRegistry) {
        MapResilienceProperties.CircuitBreaker configured = properties.getCircuitBreaker();
        this.config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(configured.getSlidingWindowSize())
            .minimumNumberOfCalls(configured.getMinimumNumberOfCalls())
            .failureRateThreshold(configured.getFailureRateThreshold())
            .waitDurationInOpenState(configured.getWaitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(configured.getPermittedCallsInHalfOpenState())
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .ignoreException(error ->
                error instanceof MapProviderConfigurationException
                    || error instanceof MapProviderRequestException
                    || error instanceof IllegalArgumentException)
            .build();
        this.meterRegistry = meterRegistry;
    }

    <T> T execute(MapProvider provider, String operation, Supplier<T> call) {
        if ("demo".equalsIgnoreCase(provider.name())) {
            return call.get();
        }

        CircuitBreaker breaker = breakers.computeIfAbsent(provider.name(), this::create);
        try {
            return breaker.executeSupplier(call);
        } catch (CallNotPermittedException exception) {
            meterRegistry.counter(
                "map.provider.circuit.rejected",
                "provider", provider.name(),
                "operation", operation
            ).increment();
            throw unavailable("map provider circuit is open", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof MapProviderConfigurationException
                || exception instanceof MapProviderRequestException
                || exception instanceof IllegalArgumentException) {
                throw exception;
            }
            meterRegistry.counter(
                "map.provider.failures",
                "provider", provider.name(),
                "operation", operation
            ).increment();
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw unavailable("map provider request failed", exception);
        }
    }

    CircuitBreaker.State state(String providerName) {
        CircuitBreaker breaker = breakers.get(providerName);
        return breaker == null ? CircuitBreaker.State.CLOSED : breaker.getState();
    }

    private CircuitBreaker create(String providerName) {
        CircuitBreaker breaker = CircuitBreaker.of("map-provider-" + providerName, config);
        Gauge.builder("map.provider.circuit.state", breaker, value -> value.getState().ordinal())
            .description("Current map provider circuit-breaker state")
            .tag("provider", providerName)
            .register(meterRegistry);
        return breaker;
    }

    private BusinessException unavailable(String message, RuntimeException cause) {
        BusinessException exception = new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "MAP_PROVIDER_UNAVAILABLE",
            message
        );
        exception.initCause(cause);
        return exception;
    }
}
