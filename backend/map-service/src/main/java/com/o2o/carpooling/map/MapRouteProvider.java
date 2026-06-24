package com.o2o.carpooling.map;

@FunctionalInterface
interface MapRouteProvider {

    RouteQuoteResult quote(RouteQuoteRequest request);

    default boolean supports() {
        return true;
    }
}
