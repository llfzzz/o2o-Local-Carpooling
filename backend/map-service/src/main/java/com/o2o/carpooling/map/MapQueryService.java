package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.CoordinateTransform;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Place lookup: reverse geocoding, autocomplete and POI search.
 *
 * <p>This class owns the datum boundary. Callers may submit WGS-84 (what a browser's Geolocation
 * API produces) or GCJ-02 (what the AMap JS API produces); everything below this point is GCJ-02.
 * Converting here rather than in each provider means there is exactly one place where a datum
 * mistake could be made.
 */
@Service
class MapQueryService {

    private final MapProviderSelector providerSelector;
    private final MapCityRegistry cityRegistry;

    MapQueryService(MapProviderSelector providerSelector, MapCityRegistry cityRegistry) {
        this.providerSelector = providerSelector;
        this.cityRegistry = cityRegistry;
    }

    /** Coordinates → structured place. Rejects locations outside the supported-city allowlist. */
    LocationRef reverseGeocode(GeoPoint point) {
        LocationRef resolved = providerSelector.active().reverseGeocode(toProviderDatum(point));
        cityRegistry.requireEnabled(resolved.adcode());
        return resolved;
    }

    List<LocationRef> suggest(PlaceQuery query) {
        return withinEnabledCities(providerSelector.active().suggest(biasedToProviderDatum(query)));
    }

    List<LocationRef> searchPoi(PlaceQuery query) {
        return withinEnabledCities(providerSelector.active().searchPoi(biasedToProviderDatum(query)));
    }

    boolean isDemoActive() {
        return providerSelector.isDemoActive();
    }

    /**
     * Suggestions outside the allowlist are filtered out rather than rejected: a keyword can
     * legitimately match places in several cities, and only some of them may be served.
     */
    private List<LocationRef> withinEnabledCities(List<LocationRef> results) {
        if (cityRegistry.isUnrestricted()) {
            return results;
        }
        return results.stream().filter(result -> cityRegistry.isEnabled(result.adcode())).toList();
    }

    private PlaceQuery biasedToProviderDatum(PlaceQuery query) {
        if (query.bias() == null) {
            return query;
        }
        return new PlaceQuery(query.keyword(), query.cityCode(), toProviderDatum(query.bias()), query.size());
    }

    private GeoPoint toProviderDatum(GeoPoint point) {
        return CoordinateTransform.toDatum(point, CoordinateDatum.GCJ02);
    }
}
