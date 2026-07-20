package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
class RouteSnapshotRepository {

    private static final Pattern JSON_KEY_FIELD = Pattern.compile("\"key\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern QUERY_KEY = Pattern.compile("key=[^&\"}]+");

    /** Text columns are varchar(200); truncate rather than fail a quote on an unusually long name. */
    private static final int TEXT_LIMIT = 200;

    private final JdbcClient jdbcClient;

    RouteSnapshotRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(RouteQuoteResult result) {
        RouteSnapshot route = result.routeSnapshot();
        LocationRef origin = route.origin();
        LocationRef destination = route.destination();

        jdbcClient.sql("""
            insert into route_snapshots (
              route_id, origin_text, destination_text, city, origin_coordinate, destination_coordinate,
              distance_meters, duration_seconds, provider, provider_trace, provider_response_snapshot, created_at,
              polyline, coordinate_datum, origin_adcode, destination_adcode,
              origin_city_code, destination_city_code, origin_place_id, destination_place_id, cache_key
            ) values (
              :routeId, :originText, :destinationText, :city, :originCoordinate, :destinationCoordinate,
              :distanceMeters, :durationSeconds, :provider, :providerTrace, :providerResponseSnapshot, :createdAt,
              :polyline, :coordinateDatum, :originAdcode, :destinationAdcode,
              :originCityCode, :destinationCityCode, :originPlaceId, :destinationPlaceId, :cacheKey
            )
            """)
            .param("routeId", route.routeId())
            .param("originText", truncate(displayText(origin, result.request().origin())))
            .param("destinationText", truncate(displayText(destination, result.request().destination())))
            .param("city", result.request().city())
            .param("originCoordinate", result.originCoordinate())
            .param("destinationCoordinate", result.destinationCoordinate())
            .param("distanceMeters", route.distanceMeters())
            .param("durationSeconds", route.durationSeconds())
            .param("provider", result.provider())
            .param("providerTrace", route.providerTrace())
            .param("providerResponseSnapshot", sanitize(result.providerResponseSnapshot()))
            .param("createdAt", Instant.now())
            .param("polyline", route.polyline())
            .param("coordinateDatum", origin == null ? null : origin.point().datum().name())
            .param("originAdcode", origin == null ? null : origin.adcode())
            .param("destinationAdcode", destination == null ? null : destination.adcode())
            .param("originCityCode", origin == null ? null : origin.cityCode())
            .param("destinationCityCode", destination == null ? null : destination.cityCode())
            .param("originPlaceId", origin == null ? null : origin.providerPlaceId())
            .param("destinationPlaceId", destination == null ? null : destination.providerPlaceId())
            .param("cacheKey", cacheKey(result))
            .update();
    }

    Optional<RouteSnapshot> findLatest(String cacheKey, Instant notBefore) {
        if (cacheKey == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
            select route_id, distance_meters, duration_seconds, provider_trace, polyline
            from route_snapshots
            where cache_key = :cacheKey and created_at >= :notBefore
            order by created_at desc
            limit 1
            """)
            .param("cacheKey", cacheKey)
            .param("notBefore", notBefore)
            .query((resultSet, rowNum) -> new RouteSnapshot(
                resultSet.getString("route_id"),
                resultSet.getInt("distance_meters"),
                resultSet.getInt("duration_seconds"),
                resultSet.getString("provider_trace"),
                resultSet.getString("polyline"),
                null,
                null
            ))
            .optional();
    }

    /**
     * Dedupe key for repeated identical quotes. Coordinates are rounded to ~100m so that two riders
     * standing near each other produce the same key.
     */
    static String cacheKey(RouteQuoteResult result) {
        RouteSnapshot route = result.routeSnapshot();
        if (route.origin() == null || route.destination() == null) {
            return null;
        }
        return cacheKey(result.provider(), route.origin(), route.destination());
    }

    static String cacheKey(String provider, LocationRef origin, LocationRef destination) {
        if (provider == null || origin == null || destination == null) {
            return null;
        }
        return provider + ":" + rounded(origin) + ">" + rounded(destination);
    }

    private static String rounded(LocationRef location) {
        return String.format(
            Locale.ROOT, "%.3f,%.3f", location.point().latitude(), location.point().longitude());
    }

    private String displayText(LocationRef location, String fallback) {
        return location != null ? location.displayName() : fallback;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, TEXT_LIMIT);
    }

    private String sanitize(String snapshot) {
        String withoutJsonKey = JSON_KEY_FIELD.matcher(snapshot == null ? "{}" : snapshot).replaceAll("\"key\":\"***\"");
        return QUERY_KEY.matcher(withoutJsonKey).replaceAll("key=***");
    }
}
