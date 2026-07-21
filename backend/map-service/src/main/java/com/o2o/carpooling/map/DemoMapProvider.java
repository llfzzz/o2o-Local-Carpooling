package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.Haversine;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Explicit demo map provider, active only when {@code providers.map.type=demo}.
 *
 * <p>Everything it returns is labelled {@code provider="demo"} / {@code providerTrace="amap-mock"}
 * so it can never be mistaken for real provider output. Results come from a small curated fixture
 * set spanning several unrelated cities — deliberately multi-city, so that demo mode cannot be the
 * reason a bug in one region goes unnoticed.
 *
 * <p>Distances are straight-line × a 1.3 road factor. That is an honest approximation, not a
 * fabricated route: the demo badge in the UI tells the user exactly what they are looking at.
 */
@Component
class DemoMapProvider implements MapProvider {

    private static final String PROVIDER = "demo";
    private static final String TRACE = "amap-mock";

    /** Winding factor from straight-line to road distance — typical for urban road networks. */
    private static final double ROAD_FACTOR = 1.3;

    /** ~30 km/h average urban speed. */
    private static final double METERS_PER_SECOND = 8.33;

    private static final List<DemoPlace> PLACES = List.of(
        // 厦门 — the historical demo route, retained as clearly-labelled seed data.
        new DemoPlace("软件园三期", "福建省厦门市集美区软件园三期", "350211", "0592", 24.4879, 118.1781),
        new DemoPlace("集美大学", "福建省厦门市集美区集美大学", "350211", "0592", 24.5751, 118.0972),
        new DemoPlace("厦门北站", "福建省厦门市集美区厦门北站", "350211", "0592", 24.6153, 118.0507),
        new DemoPlace("厦门高崎国际机场", "福建省厦门市湖里区高崎国际机场", "350206", "0592", 24.5440, 118.1273),
        new DemoPlace("中山路步行街", "福建省厦门市思明区中山路", "350203", "0592", 24.4517, 118.0819),
        // 北京
        new DemoPlace("中关村", "北京市海淀区中关村大街", "110108", "010", 39.9847, 116.3070),
        new DemoPlace("北京南站", "北京市丰台区北京南站", "110106", "010", 39.8654, 116.3786),
        new DemoPlace("首都国际机场", "北京市顺义区首都国际机场", "110113", "010", 40.0799, 116.6031),
        new DemoPlace("望京SOHO", "北京市朝阳区望京SOHO", "110105", "010", 39.9962, 116.4780),
        // 成都
        new DemoPlace("天府广场", "四川省成都市青羊区天府广场", "510105", "028", 30.6570, 104.0657),
        new DemoPlace("成都东站", "四川省成都市成华区成都东站", "510108", "028", 30.6300, 104.1414),
        new DemoPlace("春熙路", "四川省成都市锦江区春熙路", "510104", "028", 30.6516, 104.0817),
        // 哈尔滨 — deliberately far from the others, so nothing can be tuned to one latitude band.
        new DemoPlace("哈尔滨西站", "黑龙江省哈尔滨市南岗区哈尔滨西站", "230103", "0451", 45.7010, 126.5580),
        new DemoPlace("中央大街", "黑龙江省哈尔滨市道里区中央大街", "230102", "0451", 45.7732, 126.6174)
    );

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public RouteQuoteResult quote(RouteQuoteRequest request) {
        LocationRef origin = request.originRef() != null
            ? request.originRef()
            : resolveText(request.origin(), "origin");
        LocationRef destination = request.destinationRef() != null
            ? request.destinationRef()
            : resolveText(request.destination(), "destination");

        double straightLine = origin.point().distanceMetersTo(destination.point());
        int distanceMeters = Math.max(500, (int) Math.round(straightLine * ROAD_FACTOR));
        int durationSeconds = Math.max(120, (int) Math.round(distanceMeters / METERS_PER_SECOND));

        RouteSnapshot route = new RouteSnapshot(
            "route-" + UUID.randomUUID(),
            distanceMeters,
            durationSeconds,
            TRACE,
            straightLinePolyline(origin.point(), destination.point()),
            origin,
            destination
        );

        return RouteQuoteResult.from(
            request,
            route,
            PROVIDER,
            origin.point().toProviderLngLat(),
            destination.point().toProviderLngLat(),
            "{\"provider\":\"" + TRACE + "\",\"reason\":\"demo map provider (straight-line estimate, not a real route)\"}"
        );
    }

    @Override
    public LocationRef reverseGeocode(GeoPoint point) {
        GeoPoint gcj02 = requireGcj02(point);
        DemoPlace nearest = PLACES.stream()
            .min(Comparator.comparingDouble(place -> place.distanceTo(gcj02)))
            .orElseThrow();
        // Report the caller's own point, not the fixture's — the pin is where the user put it.
        return new LocationRef(
            gcj02,
            PROVIDER,
            null,
            nearest.cityCode(),
            nearest.adcode(),
            nearest.name() + "附近",
            nearest.address() + "附近",
            LocationSource.MAP_PIN,
            null,
            Instant.now()
        );
    }

    /**
     * The curated fixture places, optionally filtered to one city. Used only by the demo
     * virtual-trip "random route" feature (via an internal, unrouted endpoint) — random
     * generation is meaningless against a real provider and would burn quota, so this exists
     * only on the demo provider.
     */
    List<LocationRef> demoPlaces(String cityCode) {
        return PLACES.stream()
            .filter(place -> !StringUtils.hasText(cityCode) || place.cityCode().equals(cityCode))
            .map(place -> place.toLocationRef(LocationSource.DEMO_SEED))
            .toList();
    }

    @Override
    public List<LocationRef> suggest(PlaceQuery query) {
        return matching(query, LocationSource.AUTOCOMPLETE);
    }

    @Override
    public List<LocationRef> searchPoi(PlaceQuery query) {
        return matching(query, LocationSource.POI_SEARCH);
    }

    private List<LocationRef> matching(PlaceQuery query, LocationSource source) {
        String keyword = query.keyword();
        Stream<DemoPlace> candidates = PLACES.stream()
            .filter(place -> place.matches(keyword))
            .filter(place -> !StringUtils.hasText(query.cityCode()) || place.cityCode().equals(query.cityCode()));
        if (query.bias() != null) {
            GeoPoint bias = requireGcj02(query.bias());
            candidates = candidates.sorted(Comparator.comparingDouble(place -> place.distanceTo(bias)));
        }
        return candidates
            .limit(query.size())
            .map(place -> place.toLocationRef(source))
            .toList();
    }

    private LocationRef resolveText(String text, String label) {
        if (!StringUtils.hasText(text)) {
            throw demoResolutionFailed(label + " is required");
        }
        String trimmed = text.trim();
        // The legacy contract allows a raw "lng,lat" pair; honour it before falling back to fixtures.
        if (trimmed.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            GeoPoint point = GeoPoint.parseProviderLngLat(trimmed, CoordinateDatum.GCJ02);
            return reverseGeocode(point);
        }
        return PLACES.stream()
            .filter(place -> place.matches(trimmed))
            .findFirst()
            .map(place -> place.toLocationRef(LocationSource.DEMO_SEED))
            .orElseThrow(() -> demoResolutionFailed(
                "demo map provider has no fixture matching '" + trimmed + "'"));
    }

    /** Two-point polyline. Enough for the map to draw something honest without inventing a road path. */
    private String straightLinePolyline(GeoPoint origin, GeoPoint destination) {
        return origin.toProviderLngLat() + ";" + destination.toProviderLngLat();
    }

    private GeoPoint requireGcj02(GeoPoint point) {
        if (point.datum() != CoordinateDatum.GCJ02) {
            throw demoResolutionFailed("expected GCJ02 coordinate but got " + point.datum());
        }
        return point;
    }

    private BusinessException demoResolutionFailed(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "MAP_DEMO_LOCATION_UNKNOWN", message);
    }

    private record DemoPlace(
        String name,
        String address,
        String adcode,
        String cityCode,
        double latitude,
        double longitude
    ) {

        GeoPoint point() {
            return GeoPoint.gcj02(latitude, longitude);
        }

        boolean matches(String keyword) {
            return name.contains(keyword) || address.contains(keyword) || keyword.contains(name);
        }

        double distanceTo(GeoPoint other) {
            return Haversine.distanceMeters(latitude, longitude, other.latitude(), other.longitude());
        }

        LocationRef toLocationRef(LocationSource source) {
            return new LocationRef(
                point(),
                PROVIDER,
                "demo-poi-" + adcode + "-" + name.hashCode(),
                cityCode,
                adcode,
                name,
                address,
                source,
                null,
                Instant.now()
            );
        }
    }
}
