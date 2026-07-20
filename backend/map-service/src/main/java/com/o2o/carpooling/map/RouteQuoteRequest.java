package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.LocationRef;

/**
 * A route quote request in one of two forms.
 *
 * <p><strong>Structured</strong> ({@link #ofLocations}) is what the product uses: both endpoints are
 * already resolved, so no geocoding round-trip is needed and the persisted snapshot carries adcodes
 * and place ids.
 *
 * <p><strong>Text</strong> ({@link #ofText}) is the legacy form kept for the older
 * {@code GET /api/maps/route} contract, where the provider must geocode the strings first.
 */
record RouteQuoteRequest(
    String origin,
    String destination,
    String city,
    LocationRef originRef,
    LocationRef destinationRef
) {

    static RouteQuoteRequest ofText(String origin, String destination, String city) {
        return new RouteQuoteRequest(origin, destination, city, null, null);
    }

    static RouteQuoteRequest ofLocations(LocationRef origin, LocationRef destination) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("origin and destination are required");
        }
        return new RouteQuoteRequest(
            origin.point().toProviderLngLat(),
            destination.point().toProviderLngLat(),
            origin.cityCode(),
            origin,
            destination
        );
    }

    /** True when both endpoints are already resolved and geocoding can be skipped. */
    boolean isResolved() {
        return originRef != null && destinationRef != null;
    }
}
