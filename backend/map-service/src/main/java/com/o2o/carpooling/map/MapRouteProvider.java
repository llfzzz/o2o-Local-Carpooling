package com.o2o.carpooling.map;

/**
 * Provider seam for route quoting. The provider is selected explicitly via {@code providers.map.type}
 * (demo → {@link MockRouteProvider}, amap → {@link AmapRouteProvider}); a configured real provider
 * that fails is never silently downgraded to the mock — it fails closed.
 */
interface MapRouteProvider {

    /** Provider key, matched against providers.map.type. */
    String name();

    RouteQuoteResult quote(RouteQuoteRequest request);
}
