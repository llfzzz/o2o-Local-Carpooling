package com.o2o.carpooling.common.domain;

/**
 * How a {@link LocationRef} was obtained. Recorded so the server can tell a provider-resolved
 * place from a user-dragged pin, and so demo data is never mistakable for real provider output.
 */
public enum LocationSource {

    /** Browser Geolocation API reading (WGS-84 before conversion). */
    GEOLOCATION,

    /** Picked from provider keyword autocomplete / input tips. */
    AUTOCOMPLETE,

    /** Picked from a provider POI search result. */
    POI_SEARCH,

    /** Dragged pin on the map, resolved by reverse geocoding. */
    MAP_PIN,

    /** Typed address resolved by forward geocoding. */
    MANUAL,

    /** Curated demo fixture. Never produced by a real provider. */
    DEMO_SEED
}
