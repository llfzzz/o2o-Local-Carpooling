package com.o2o.carpooling.common.domain;

import java.math.BigDecimal;

/**
 * Server-authoritative per-seat fare breakdown produced by {@link PricingPolicy#quoteBreakdown}.
 * Every component is displayed as-is by clients; the frontend performs no price arithmetic.
 *
 * @param distanceMeters route distance from the server RouteSnapshot
 * @param distanceKm     distance in km, scale 3, HALF_UP
 * @param baseFare       fixed base fare which already covers {@code includedKm}
 * @param includedKm     distance covered by the base fare, scale 3
 * @param chargeableKm   max(0, distanceKm − includedKm), scale 3
 * @param extraCharge    chargeableKm × extraKilometerFare, scale 2, HALF_UP
 * @param total          final per-seat fare: max(minFare, baseFare + extraCharge)
 * @param currency       currency of every monetary component
 */
public record PriceBreakdown(
    int distanceMeters,
    BigDecimal distanceKm,
    BigDecimal baseFare,
    BigDecimal includedKm,
    BigDecimal chargeableKm,
    BigDecimal extraCharge,
    Money total,
    String currency
) {
}
