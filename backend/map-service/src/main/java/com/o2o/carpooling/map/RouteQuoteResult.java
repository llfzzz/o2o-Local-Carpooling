package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;

record RouteQuoteResult(
    RouteQuoteRequest request,
    RouteSnapshot routeSnapshot,
    String provider,
    String originCoordinate,
    String destinationCoordinate,
    String providerResponseSnapshot
) {

    static RouteQuoteResult from(
        RouteQuoteRequest request,
        RouteSnapshot routeSnapshot,
        String provider,
        String originCoordinate,
        String destinationCoordinate,
        String providerResponseSnapshot
    ) {
        return new RouteQuoteResult(
            request,
            routeSnapshot,
            provider,
            originCoordinate,
            destinationCoordinate,
            providerResponseSnapshot == null ? "{}" : providerResponseSnapshot
        );
    }
}
