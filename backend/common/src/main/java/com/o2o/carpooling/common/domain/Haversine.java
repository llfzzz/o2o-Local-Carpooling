package com.o2o.carpooling.common.domain;

/** Great-circle distance on a spherical earth. Accurate to ~0.5% — ample for match radii. */
public final class Haversine {

    static final double EARTH_RADIUS_METERS = 6_371_008.8;

    /**
     * Metres per degree of latitude under the same spherical model this class measures with.
     * Anything deriving a bounding box for a radius must use this, or the box can under-select
     * and silently drop candidates that a real distance check would have accepted.
     */
    static final double METERS_PER_LATITUDE_DEGREE = Math.PI * EARTH_RADIUS_METERS / 180.0;

    private Haversine() {
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
