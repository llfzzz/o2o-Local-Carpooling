package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class RouteQuoteService {

    private final MapProviderSelector providerSelector;
    private final MapCityRegistry cityRegistry;
    private final RouteSnapshotRepository repository;

    RouteQuoteService(
        MapProviderSelector providerSelector,
        MapCityRegistry cityRegistry,
        RouteSnapshotRepository repository
    ) {
        this.providerSelector = providerSelector;
        this.cityRegistry = cityRegistry;
        this.repository = repository;
    }

    /** Legacy text form, retained for the older {@code GET /api/maps/route} contract. */
    RouteSnapshot quote(RouteQuoteRequest request) {
        validate(request);
        RouteQuoteResult result = providerSelector.active().quote(request);
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
        RouteQuoteResult result = providerSelector.active().quote(RouteQuoteRequest.ofLocations(origin, destination));
        repository.save(result);
        return result.routeSnapshot();
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
