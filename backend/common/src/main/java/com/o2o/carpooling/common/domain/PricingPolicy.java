package com.o2o.carpooling.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Server-authoritative per-seat fare:
 *
 * <pre>fare = max(minFare, baseFare + max(0, distanceKm − includedKm) × perKilometerExtra)</pre>
 *
 * <p>Rounding rules (documented and tested — do not change silently):
 * <ul>
 *   <li>distanceKm = distanceMeters / 1000 at scale 3, HALF_UP;</li>
 *   <li>chargeableKm = max(0, distanceKm − includedKm) at scale 3;</li>
 *   <li>extraCharge = chargeableKm × perKilometerExtra rounded to scale 2, HALF_UP;</li>
 *   <li>total = max(minFare, baseFare + extraCharge), normalized to scale 2 by {@link Money}.</li>
 * </ul>
 *
 * All arithmetic is BigDecimal; floating point never touches money.
 */
public final class PricingPolicy {

    private static final BigDecimal METERS_PER_KILOMETER = new BigDecimal("1000");

    private final BigDecimal baseFare;
    private final BigDecimal includedKm;
    private final BigDecimal perKilometerExtra;
    private final BigDecimal minFare;
    private final String currency;

    public PricingPolicy(BigDecimal baseFare, BigDecimal includedKm, BigDecimal perKilometerExtra,
                         BigDecimal minFare, String currency) {
        this.baseFare = requireNonNegative(baseFare, "baseFare");
        this.includedKm = requireNonNegative(includedKm, "includedKm");
        this.perKilometerExtra = requireNonNegative(perKilometerExtra, "perKilometerExtra");
        this.minFare = requireNonNegative(minFare, "minFare");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        this.currency = currency;
    }

    /** Legacy shape (no included distance, no floor): fare = base + km × perKm, in CNY. */
    public PricingPolicy(BigDecimal baseFare, BigDecimal perKilometerFare) {
        this(baseFare, BigDecimal.ZERO, perKilometerFare, BigDecimal.ZERO, "CNY");
    }

    public Money quote(RouteSnapshot route) {
        return quoteBreakdown(route).total();
    }

    public PriceBreakdown quoteBreakdown(RouteSnapshot route) {
        BigDecimal distanceKm = BigDecimal.valueOf(route.distanceMeters())
            .divide(METERS_PER_KILOMETER, 3, RoundingMode.HALF_UP);
        BigDecimal chargeableKm = distanceKm.subtract(includedKm).max(BigDecimal.ZERO)
            .setScale(3, RoundingMode.HALF_UP);
        BigDecimal extraCharge = chargeableKm.multiply(perKilometerExtra)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = baseFare.add(extraCharge).max(minFare);
        return new PriceBreakdown(
            route.distanceMeters(),
            distanceKm,
            baseFare.setScale(2, RoundingMode.HALF_UP),
            includedKm,
            chargeableKm,
            extraCharge,
            new Money(total, currency),
            currency
        );
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
