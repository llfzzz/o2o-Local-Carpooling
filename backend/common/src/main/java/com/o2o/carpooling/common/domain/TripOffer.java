package com.o2o.carpooling.common.domain;

import java.time.Instant;

public record TripOffer(
    String tripId,
    String driverId,
    String originText,
    String destinationText,
    Instant departureAt,
    RouteSnapshot route,
    SeatInventory inventory,
    Money seatPrice,
    TripStatus status,
    /** Per-seat fare breakdown; null for trips published before pricing components were stored. */
    PriceBreakdown priceBreakdown,
    /** USER for real trips; DEMO for demo-generated virtual trips (badged as demo data). */
    TripSource source
) {

    /** Compat shape without a breakdown or source (pre-migration rows, tests, fakes). */
    public TripOffer(
        String tripId,
        String driverId,
        String originText,
        String destinationText,
        Instant departureAt,
        RouteSnapshot route,
        SeatInventory inventory,
        Money seatPrice,
        TripStatus status
    ) {
        this(tripId, driverId, originText, destinationText, departureAt, route, inventory, seatPrice, status, null, TripSource.USER);
    }

    /** Compat shape with a breakdown but no explicit source (defaults to USER). */
    public TripOffer(
        String tripId,
        String driverId,
        String originText,
        String destinationText,
        Instant departureAt,
        RouteSnapshot route,
        SeatInventory inventory,
        Money seatPrice,
        TripStatus status,
        PriceBreakdown priceBreakdown
    ) {
        this(tripId, driverId, originText, destinationText, departureAt, route, inventory, seatPrice, status, priceBreakdown, TripSource.USER);
    }
}
