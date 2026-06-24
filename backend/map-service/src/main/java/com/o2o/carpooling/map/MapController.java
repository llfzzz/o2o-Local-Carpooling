package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/map", "/api/maps"})
class MapController {

    private final RouteQuoteService routeQuoteService;

    MapController(RouteQuoteService routeQuoteService) {
        this.routeQuoteService = routeQuoteService;
    }

    @GetMapping("/route")
    RouteSnapshot quoteRoute(
        @RequestParam String origin,
        @RequestParam String destination,
        @RequestParam(required = false) String city
    ) {
        return routeQuoteService.quote(new RouteQuoteRequest(origin, destination, city));
    }
}
