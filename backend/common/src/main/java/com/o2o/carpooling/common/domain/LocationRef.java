package com.o2o.carpooling.common.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A place that has been resolved to coordinates by a map provider.
 *
 * <p>This is the only shape a location may take once it enters the business flow. Free-form text
 * is an <em>input</em> to resolution, never a location itself: routes, trips and orders are built
 * from {@code LocationRef} so that "where" always carries coordinates, a datum, an administrative
 * area and the identity of the provider that vouched for it.
 *
 * @param point            coordinates plus their datum; never null
 * @param provider         resolving provider key, matching {@code providers.map.type} ("amap", "demo")
 * @param providerPlaceId  provider POI id; null for a dragged pin, which names no POI
 * @param cityCode         provider city code (AMap {@code citycode})
 * @param adcode           administrative area code — the multi-city key; never null
 * @param displayName      short label shown to users
 * @param formattedAddress full address line
 * @param source           how the user arrived at this place
 * @param accuracyMeters   reported accuracy; null when the provider gives none
 * @param capturedAt       when this resolution happened, for staleness checks
 */
public record LocationRef(
    GeoPoint point,
    String provider,
    String providerPlaceId,
    String cityCode,
    String adcode,
    String displayName,
    String formattedAddress,
    LocationSource source,
    Integer accuracyMeters,
    Instant capturedAt
) {

    public LocationRef {
        Objects.requireNonNull(point, "point is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (adcode == null || adcode.isBlank()) {
            throw new IllegalArgumentException("adcode is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (accuracyMeters != null && accuracyMeters < 0) {
            throw new IllegalArgumentException("accuracyMeters must not be negative");
        }
    }

    /** True when this location came from the demo provider and must be labelled as such in the UI. */
    public boolean isDemo() {
        return "demo".equalsIgnoreCase(provider) || source == LocationSource.DEMO_SEED;
    }

    public boolean sameCityAs(LocationRef other) {
        return other != null && adcode.equals(other.adcode);
    }
}
