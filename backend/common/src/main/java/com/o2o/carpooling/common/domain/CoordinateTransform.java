package com.o2o.carpooling.common.domain;

import java.util.Objects;

/**
 * The single WGS-84 &harr; GCJ-02 boundary in this codebase.
 *
 * <p>The browser Geolocation API emits WGS-84; AMap (both the JS API and the web service) emits
 * and consumes GCJ-02. Inside China the two differ by roughly 500m, so passing one where the
 * other is expected silently places the user several streets away. Every coordinate crossing
 * that boundary goes through {@link #toDatum}.
 *
 * <p>The obfuscation applied by GCJ-02 is not publicly specified; this is the widely-used
 * approximation, accurate to a few metres. It is deliberately a no-op outside China's bounding
 * box, matching the real system's behaviour.
 */
public final class CoordinateTransform {

    /** Krasovsky 1940 semi-major axis, the ellipsoid GCJ-02 is defined against. */
    private static final double A = 6_378_245.0;

    /** Squared eccentricity of that ellipsoid. */
    private static final double EE = 0.006_693_421_622_965_943;

    /** Iterations for the inverse transform; 3 already converges below 1cm. */
    private static final int INVERSE_ITERATIONS = 4;

    private CoordinateTransform() {
    }

    /** Converts {@code point} into {@code target}, returning it unchanged when already correct. */
    public static GeoPoint toDatum(GeoPoint point, CoordinateDatum target) {
        Objects.requireNonNull(point, "point is required");
        Objects.requireNonNull(target, "target is required");
        if (point.datum() == target) {
            return point;
        }
        return target == CoordinateDatum.GCJ02 ? wgs84ToGcj02(point) : gcj02ToWgs84(point);
    }

    public static GeoPoint wgs84ToGcj02(GeoPoint point) {
        requireDatum(point, CoordinateDatum.WGS84);
        double lat = point.latitude();
        double lng = point.longitude();
        if (outOfChina(lat, lng)) {
            return new GeoPoint(lat, lng, CoordinateDatum.GCJ02);
        }
        double[] offset = offset(lat, lng);
        return new GeoPoint(lat + offset[0], lng + offset[1], CoordinateDatum.GCJ02);
    }

    /**
     * Inverse of {@link #wgs84ToGcj02}. The forward transform has no closed-form inverse, so this
     * refines a guess until applying the forward transform reproduces the input.
     */
    public static GeoPoint gcj02ToWgs84(GeoPoint point) {
        requireDatum(point, CoordinateDatum.GCJ02);
        double targetLat = point.latitude();
        double targetLng = point.longitude();
        if (outOfChina(targetLat, targetLng)) {
            return new GeoPoint(targetLat, targetLng, CoordinateDatum.WGS84);
        }
        double lat = targetLat;
        double lng = targetLng;
        for (int i = 0; i < INVERSE_ITERATIONS; i++) {
            double[] offset = offset(lat, lng);
            lat = targetLat - offset[0];
            lng = targetLng - offset[1];
        }
        return new GeoPoint(lat, lng, CoordinateDatum.WGS84);
    }

    /** Returns {@code {deltaLat, deltaLng}} in degrees for a WGS-84 position. */
    private static double[] offset(double lat, double lng) {
        double x = lng - 105.0;
        double y = lat - 35.0;
        double deltaLat = transformLat(x, y);
        double deltaLng = transformLng(x, y);
        double radLat = Math.toRadians(lat);
        double magic = 1 - EE * Math.sin(radLat) * Math.sin(radLat);
        double sqrtMagic = Math.sqrt(magic);
        deltaLat = (deltaLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        deltaLng = (deltaLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[] {deltaLat, deltaLng};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    /** GCJ-02 obfuscation applies only inside China's bounding box. */
    static boolean outOfChina(double lat, double lng) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static void requireDatum(GeoPoint point, CoordinateDatum expected) {
        Objects.requireNonNull(point, "point is required");
        if (point.datum() != expected) {
            throw new IllegalArgumentException("expected " + expected + " coordinate but got " + point.datum());
        }
    }
}
