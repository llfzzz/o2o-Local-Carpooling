package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/map", "/api/maps"})
class MapController {

    private final RouteQuoteService routeQuoteService;
    private final MapQueryService mapQueryService;
    private final MapCityRegistry cityRegistry;

    MapController(
        RouteQuoteService routeQuoteService,
        MapQueryService mapQueryService,
        MapCityRegistry cityRegistry
    ) {
        this.routeQuoteService = routeQuoteService;
        this.mapQueryService = mapQueryService;
        this.cityRegistry = cityRegistry;
    }

    /**
     * Supported cities plus whether the active provider is the demo one, so the client can show the
     * demo badge without inferring it from result contents.
     */
    @GetMapping("/cities")
    Map<String, Object> cities() {
        return Map.of(
            "unrestricted", cityRegistry.isUnrestricted(),
            "demoProvider", mapQueryService.isDemoActive(),
            "cities", cityRegistry.describe()
        );
    }

    @PostMapping("/reverse-geocode")
    LocationRef reverseGeocode(@RequestBody ReverseGeocodeRequest request) {
        return mapQueryService.reverseGeocode(request.toGeoPoint());
    }

    @GetMapping("/place/suggest")
    List<LocationRef> suggest(
        @RequestParam String keyword,
        @RequestParam(required = false) String cityCode,
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng,
        @RequestParam(required = false) CoordinateDatum datum,
        @RequestParam(required = false) Integer size
    ) {
        return mapQueryService.suggest(PlaceQuery.of(keyword, cityCode, bias(lat, lng, datum), size));
    }

    @GetMapping("/place/search")
    List<LocationRef> searchPoi(
        @RequestParam String keyword,
        @RequestParam(required = false) String cityCode,
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng,
        @RequestParam(required = false) CoordinateDatum datum,
        @RequestParam(required = false) Integer size
    ) {
        return mapQueryService.searchPoi(PlaceQuery.of(keyword, cityCode, bias(lat, lng, datum), size));
    }

    /** Structured route quote — both endpoints already resolved to places. */
    @PostMapping("/route")
    RouteSnapshot quoteRoute(@RequestBody StructuredRouteRequest request) {
        if (request.origin() == null || request.destination() == null) {
            throw new IllegalArgumentException("origin and destination are required");
        }
        return routeQuoteService.quote(request.origin(), request.destination());
    }

    /**
     * Legacy text form. Retained for older clients; superseded by {@code POST /api/maps/route},
     * which does not require the provider to guess what the text meant.
     */
    @GetMapping("/route")
    RouteSnapshot quoteRoute(
        @RequestParam String origin,
        @RequestParam String destination,
        @RequestParam(required = false) String city
    ) {
        return routeQuoteService.quote(RouteQuoteRequest.ofText(origin, destination, city));
    }

    private GeoPoint bias(Double lat, Double lng, CoordinateDatum datum) {
        if (lat == null || lng == null) {
            return null;
        }
        return new GeoPoint(lat, lng, datum == null ? CoordinateDatum.GCJ02 : datum);
    }

    /**
     * @param datum required: the caller must state which system its coordinates are in. Browser
     *              geolocation is WGS-84, the AMap JS API is GCJ-02, and they differ by ~500m.
     */
    record ReverseGeocodeRequest(Double lat, Double lng, CoordinateDatum datum) {

        GeoPoint toGeoPoint() {
            if (lat == null || lng == null) {
                throw new IllegalArgumentException("lat and lng are required");
            }
            if (datum == null) {
                throw new IllegalArgumentException("datum is required");
            }
            return new GeoPoint(lat, lng, datum);
        }
    }

    record StructuredRouteRequest(LocationRef origin, LocationRef destination) {
    }
}
