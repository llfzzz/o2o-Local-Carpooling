package com.o2o.carpooling.common.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether a published trip is 顺路 for a rider, and how good a match it is.
 *
 * <p>A trip matches when its origin is within {@code originRadiusMeters} of the rider's origin,
 * its destination within {@code destinationRadiusMeters} of the rider's destination, and its
 * departure falls inside the rider's time window. Radii and window are configuration, never
 * constants in code — a city with sparse coverage may need wider radii than a dense one.
 *
 * <p>Pure domain logic with no I/O, so the matching rules can be tested directly.
 */
public record GeoMatchingPolicy(
    int originRadiusMeters,
    int destinationRadiusMeters,
    Duration departureWindow,
    int maxResults
) {

    /** Widens the pre-filter box slightly so rounding can never make it under-select. */
    private static final double SAFETY_MARGIN = 1.01;

    public GeoMatchingPolicy {
        if (originRadiusMeters <= 0 || destinationRadiusMeters <= 0) {
            throw new IllegalArgumentException("match radii must be positive");
        }
        if (departureWindow == null || departureWindow.isNegative() || departureWindow.isZero()) {
            throw new IllegalArgumentException("departureWindow must be positive");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
    }

    /**
     * Half-width of the latitude/longitude box that safely contains the larger of the two radii.
     * Used as an indexed SQL pre-filter before exact distance is computed; it over-selects (a box
     * circumscribes the circle) but must never under-select.
     */
    public double boundingBoxLatitudeDegrees() {
        return metersToLatitudeDegrees(Math.max(originRadiusMeters, destinationRadiusMeters));
    }

    /** Longitude degrees shrink with latitude, so the box widens as you approach the poles. */
    public double boundingBoxLongitudeDegrees(double atLatitude) {
        double cos = Math.cos(Math.toRadians(atLatitude));
        // Guard against the pole singularity; near the poles just take the whole span.
        if (cos < 0.01) {
            return 180.0;
        }
        return boundingBoxLatitudeDegrees() / cos;
    }

    public boolean originWithinRadius(double distanceMeters) {
        return distanceMeters <= originRadiusMeters;
    }

    public boolean destinationWithinRadius(double distanceMeters) {
        return distanceMeters <= destinationRadiusMeters;
    }

    public boolean departureWithinWindow(Instant riderDepartureAt, Instant tripDepartureAt) {
        if (riderDepartureAt == null || tripDepartureAt == null) {
            return true;
        }
        return Math.abs(Duration.between(riderDepartureAt, tripDepartureAt).toSeconds())
            <= departureWindow.toSeconds();
    }

    public boolean matches(
        double originDistanceMeters,
        double destinationDistanceMeters,
        Instant riderDepartureAt,
        Instant tripDepartureAt
    ) {
        return originWithinRadius(originDistanceMeters)
            && destinationWithinRadius(destinationDistanceMeters)
            && departureWithinWindow(riderDepartureAt, tripDepartureAt);
    }

    /**
     * Lower is better. Detour distance dominates; the time gap is a tie-breaker worth up to roughly
     * one radius of penalty at the window edge, so a much closer trip still wins over a
     * better-timed but distant one.
     */
    public double score(
        double originDistanceMeters,
        double destinationDistanceMeters,
        Instant riderDepartureAt,
        Instant tripDepartureAt
    ) {
        double distanceScore = originDistanceMeters + destinationDistanceMeters;
        if (riderDepartureAt == null || tripDepartureAt == null) {
            return distanceScore;
        }
        double gapSeconds = Math.abs(Duration.between(riderDepartureAt, tripDepartureAt).toSeconds());
        double timePenalty = gapSeconds / departureWindow.toSeconds() * originRadiusMeters;
        return distanceScore + timePenalty;
    }

    /**
     * Uses the same earth model as {@link Haversine}, plus a small margin. The pre-filter must
     * over-select rather than under-select: a box that is a few metres too small silently drops
     * candidates the real distance check would have accepted.
     */
    private static double metersToLatitudeDegrees(int meters) {
        return meters * SAFETY_MARGIN / Haversine.METERS_PER_LATITUDE_DEGREE;
    }
}
