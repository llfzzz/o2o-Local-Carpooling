package com.o2o.carpooling.common.domain;

/**
 * A server-authoritative route quote.
 *
 * <p>{@code polyline}, {@code origin} and {@code destination} are nullable: they were added when
 * the map stopped being a placeholder, and rows written before that carry only the four original
 * components. The 4-arg constructor is retained for those call sites and for tests that do not
 * care about geometry.
 *
 * @param polyline    encoded route geometry as returned by the provider; null when not requested
 * @param origin      resolved start; null on legacy text-only quotes
 * @param destination resolved end; null on legacy text-only quotes
 */
public record RouteSnapshot(
    String routeId,
    int distanceMeters,
    int durationSeconds,
    String providerTrace,
    String polyline,
    LocationRef origin,
    LocationRef destination
) {

    public RouteSnapshot {
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required");
        }
        if (distanceMeters <= 0) {
            throw new IllegalArgumentException("distanceMeters must be positive");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (providerTrace == null || providerTrace.isBlank()) {
            throw new IllegalArgumentException("providerTrace is required");
        }
    }

    public RouteSnapshot(String routeId, int distanceMeters, int durationSeconds, String providerTrace) {
        this(routeId, distanceMeters, durationSeconds, providerTrace, null, null, null);
    }

    /** True when this quote carries geometry a map can actually draw. */
    public boolean hasGeometry() {
        return polyline != null && !polyline.isBlank();
    }
}
