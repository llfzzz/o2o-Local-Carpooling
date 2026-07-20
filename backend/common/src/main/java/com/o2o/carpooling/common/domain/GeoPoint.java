package com.o2o.carpooling.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A latitude/longitude pair that always knows which datum it is expressed in.
 *
 * <p>There is deliberately no constructor accepting an unlabeled pair: the whole point of this
 * type is that a coordinate cannot travel through the system without its datum. Values are
 * normalized to 7 decimal places (~1cm) so they round-trip stably through {@code DECIMAL(10,7)}
 * columns.
 */
public record GeoPoint(double latitude, double longitude, CoordinateDatum datum) {

    private static final int SCALE = 7;

    public GeoPoint {
        Objects.requireNonNull(datum, "datum is required");
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be within [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be within [-180, 180]");
        }
        latitude = round(latitude);
        longitude = round(longitude);
    }

    public static GeoPoint wgs84(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude, CoordinateDatum.WGS84);
    }

    public static GeoPoint gcj02(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude, CoordinateDatum.GCJ02);
    }

    /**
     * Parses the {@code "lng,lat"} form used by AMap web-service responses. The caller must state
     * the datum, because the wire format does not carry one.
     */
    public static GeoPoint parseProviderLngLat(String lngLat, CoordinateDatum datum) {
        if (lngLat == null || lngLat.isBlank()) {
            throw new IllegalArgumentException("coordinate is required");
        }
        String[] parts = lngLat.trim().split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("coordinate must be in 'lng,lat' form");
        }
        try {
            return new GeoPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[0]), datum);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("coordinate must be in 'lng,lat' form");
        }
    }

    /** Renders the {@code "lng,lat"} form AMap web-service query parameters expect. */
    public String toProviderLngLat() {
        return decimal(longitude).toPlainString() + "," + decimal(latitude).toPlainString();
    }

    public BigDecimal latitudeDecimal() {
        return decimal(latitude);
    }

    public BigDecimal longitudeDecimal() {
        return decimal(longitude);
    }

    /** Great-circle distance in metres. Both points must share a datum — mixing them is a bug. */
    public double distanceMetersTo(GeoPoint other) {
        Objects.requireNonNull(other, "other is required");
        if (datum != other.datum) {
            throw new IllegalArgumentException(
                "cannot measure distance between " + datum + " and " + other.datum + " coordinates");
        }
        return Haversine.distanceMeters(latitude, longitude, other.latitude, other.longitude);
    }

    private static double round(double value) {
        return decimal(value).doubleValue();
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
