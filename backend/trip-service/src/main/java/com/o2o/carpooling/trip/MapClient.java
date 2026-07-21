package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.RouteSnapshot;

import java.util.List;

interface MapClient {

    /** Legacy text form: the provider must geocode the strings first. */
    RouteSnapshot quoteRoute(String origin, String destination, String city);

    /** Structured form: both endpoints already resolved, so the quote carries geometry and adcodes. */
    RouteSnapshot quoteRoute(LocationRef origin, LocationRef destination);

    /** Demo-only fixture places for a city (internal endpoint; 404 unless demo map provider active). */
    List<LocationRef> demoPlaces(String cityCode);
}
