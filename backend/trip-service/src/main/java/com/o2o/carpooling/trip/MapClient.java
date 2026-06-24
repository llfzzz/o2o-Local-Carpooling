package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.RouteSnapshot;

interface MapClient {

    RouteSnapshot quoteRoute(String origin, String destination, String city);
}
