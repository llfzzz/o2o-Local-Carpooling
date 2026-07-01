package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
class RouteQuoteService {

    private final List<MapRouteProvider> providers;
    private final ProviderProperties providerProperties;
    private final RouteSnapshotRepository repository;

    RouteQuoteService(
        List<MapRouteProvider> providers,
        ProviderProperties providerProperties,
        RouteSnapshotRepository repository
    ) {
        this.providers = providers;
        this.providerProperties = providerProperties;
        this.repository = repository;
    }

    RouteSnapshot quote(RouteQuoteRequest request) {
        validate(request);
        RouteQuoteResult result = provider().quote(request);
        repository.save(result);
        return result.routeSnapshot();
    }

    /**
     * Selects the provider by {@code providers.map.type} and fails closed when it has no adapter —
     * matching the SMS/payment/OCR/identity seams. A configured provider that fails at call time is
     * never silently downgraded to the mock (that rule lives in {@link AmapRouteProvider}).
     */
    private MapRouteProvider provider() {
        String type = providerProperties.getMap().getType();
        return providers.stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(type))
            .findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "MAP_PROVIDER_UNCONFIGURED",
                "no map provider configured for type '" + type + "'"));
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
