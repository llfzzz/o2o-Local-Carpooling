package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "map-service", contextId = "tripMapClient", url = "${O2O_MAP_SERVICE_URL:http://127.0.0.1:8107}")
interface MapFeignClient {

    @GetMapping("/api/maps/route")
    RouteSnapshot quoteRoute(
        @RequestParam("origin") String origin,
        @RequestParam("destination") String destination,
        @RequestParam(value = "city", required = false) String city
    );
}

@Component
class FeignMapClient implements MapClient {

    private final MapFeignClient mapFeignClient;

    FeignMapClient(MapFeignClient mapFeignClient) {
        this.mapFeignClient = mapFeignClient;
    }

    @Override
    public RouteSnapshot quoteRoute(String origin, String destination, String city) {
        return mapFeignClient.quoteRoute(origin, destination, city);
    }
}
