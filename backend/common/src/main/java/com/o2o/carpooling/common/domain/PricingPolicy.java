package com.o2o.carpooling.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class PricingPolicy {

    private static final BigDecimal METERS_PER_KILOMETER = new BigDecimal("1000");
    private final BigDecimal baseFare;
    private final BigDecimal perKilometerFare;

    public PricingPolicy(BigDecimal baseFare, BigDecimal perKilometerFare) {
        this.baseFare = requireNonNegative(baseFare, "baseFare");
        this.perKilometerFare = requireNonNegative(perKilometerFare, "perKilometerFare");
    }

    public Money quote(RouteSnapshot route) {
        BigDecimal kilometers = BigDecimal.valueOf(route.distanceMeters())
            .divide(METERS_PER_KILOMETER, 3, RoundingMode.HALF_UP);
        return new Money(baseFare.add(kilometers.multiply(perKilometerFare)), "CNY");
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
