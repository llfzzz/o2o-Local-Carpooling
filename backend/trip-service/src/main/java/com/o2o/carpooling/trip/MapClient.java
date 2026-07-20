package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.RouteSnapshot;

interface MapClient {

    /** Legacy text form: the provider must geocode the strings first. */
    RouteSnapshot quoteRoute(String origin, String destination, String city);

    /** Structured form: both endpoints already resolved, so the quote carries geometry and adcodes. */
    RouteSnapshot quoteRoute(LocationRef origin, LocationRef destination);
}
