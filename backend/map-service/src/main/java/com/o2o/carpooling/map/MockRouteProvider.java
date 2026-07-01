package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class MockRouteProvider implements MapRouteProvider {

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public RouteQuoteResult quote(RouteQuoteRequest request) {
        int distanceMeters = Math.max(5_000, (request.origin().length() + request.destination().length()) * 1_200);
        int durationSeconds = Math.max(300, distanceMeters / 8);
        RouteSnapshot route = new RouteSnapshot("route-" + UUID.randomUUID(), distanceMeters, durationSeconds, "amap-mock");
        return RouteQuoteResult.from(
            request,
            route,
            "amap-mock",
            null,
            null,
            "{\"provider\":\"amap-mock\",\"reason\":\"demo map provider (not a real route)\"}"
        );
    }
}
