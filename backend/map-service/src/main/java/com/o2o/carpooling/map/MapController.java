package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/map", "/api/maps"})
class MapController {

    @GetMapping("/route")
    RouteSnapshot quoteRoute(@RequestParam String origin, @RequestParam String destination) {
        int distanceMeters = Math.max(5000, (origin.length() + destination.length()) * 1200);
        int durationSeconds = distanceMeters / 8;
        return new RouteSnapshot("route-" + UUID.randomUUID(), distanceMeters, durationSeconds, "amap-mock");
    }
}
