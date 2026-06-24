package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class RouteQuoteService {

    private final AmapProperties properties;
    private final MapRouteProvider amapRouteProvider;
    private final MapRouteProvider mockRouteProvider;
    private final RouteSnapshotRepository repository;

    RouteQuoteService(
        AmapProperties properties,
        AmapRouteProvider amapRouteProvider,
        MockRouteProvider mockRouteProvider,
        RouteSnapshotRepository repository
    ) {
        this(properties, (MapRouteProvider) amapRouteProvider, mockRouteProvider, repository);
    }

    RouteQuoteService(
        AmapProperties properties,
        MapRouteProvider amapRouteProvider,
        MapRouteProvider mockRouteProvider,
        RouteSnapshotRepository repository
    ) {
        this.properties = properties;
        this.amapRouteProvider = amapRouteProvider;
        this.mockRouteProvider = mockRouteProvider;
        this.repository = repository;
    }

    RouteSnapshot quote(RouteQuoteRequest request) {
        validate(request);
        MapRouteProvider provider = StringUtils.hasText(properties.getApiKey()) ? amapRouteProvider : mockRouteProvider;
        RouteQuoteResult result = provider.quote(request);
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
