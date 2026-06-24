package com.o2o.carpooling.map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.regex.Pattern;

@Repository
class RouteSnapshotRepository {

    private static final Pattern JSON_KEY_FIELD = Pattern.compile("\"key\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern QUERY_KEY = Pattern.compile("key=[^&\"}]+");

    private final JdbcClient jdbcClient;

    RouteSnapshotRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(RouteQuoteResult result) {
        jdbcClient.sql("""
            insert into route_snapshots (
              route_id, origin_text, destination_text, city, origin_coordinate, destination_coordinate,
              distance_meters, duration_seconds, provider, provider_trace, provider_response_snapshot, created_at
            ) values (
              :routeId, :originText, :destinationText, :city, :originCoordinate, :destinationCoordinate,
              :distanceMeters, :durationSeconds, :provider, :providerTrace, :providerResponseSnapshot, :createdAt
            )
            """)
            .param("routeId", result.routeSnapshot().routeId())
            .param("originText", result.request().origin())
            .param("destinationText", result.request().destination())
            .param("city", result.request().city())
            .param("originCoordinate", result.originCoordinate())
            .param("destinationCoordinate", result.destinationCoordinate())
            .param("distanceMeters", result.routeSnapshot().distanceMeters())
            .param("durationSeconds", result.routeSnapshot().durationSeconds())
            .param("provider", result.provider())
            .param("providerTrace", result.routeSnapshot().providerTrace())
            .param("providerResponseSnapshot", sanitize(result.providerResponseSnapshot()))
            .param("createdAt", Instant.now())
            .update();
    }

    private String sanitize(String snapshot) {
        String withoutJsonKey = JSON_KEY_FIELD.matcher(snapshot == null ? "{}" : snapshot).replaceAll("\"key\":\"***\"");
        return QUERY_KEY.matcher(withoutJsonKey).replaceAll("key=***");
    }
}
