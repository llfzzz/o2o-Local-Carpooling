package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Pins the documented formula and rounding rules:
 * fare = max(minFare, base + max(0, km − includedKm) × perKm);
 * km at scale 3 HALF_UP; extra charge and totals at scale 2 HALF_UP; BigDecimal only.
 */
class PricingPolicyTest {

    /** base ¥6.00 covers the first 3 km; ¥1.20/km beyond; floor ¥6.00. */
    private final PricingPolicy policy = new PricingPolicy(
        new BigDecimal("6.00"), new BigDecimal("3.0"), new BigDecimal("1.20"),
        new BigDecimal("6.00"), "CNY");

    private RouteSnapshot route(int distanceMeters) {
        return new RouteSnapshot("route-1", distanceMeters, 600, "mock-amap-v1");
    }

    @Test
    void legacyShapePricesBaseFarePlusDistance() {
        PricingPolicy legacy = new PricingPolicy(new BigDecimal("6.00"), new BigDecimal("1.20"));

        Money price = legacy.quote(new RouteSnapshot("route-1", 18500, 2100, "mock-amap-v1"));

        assertThat(price.amount()).isEqualByComparingTo("28.20");
        assertThat(price.currency()).isEqualTo("CNY");
    }

    @Test
    void zeroDistanceIsRejectedUpstreamAndMinimalDistanceChargesOnlyTheBaseFare() {
        // A zero-distance route cannot exist: RouteSnapshot (the only pricing input) rejects it,
        // so the pricing boundary below the included distance starts at 1 meter.
        assertThatIllegalArgumentException().isThrownBy(() -> route(0));

        PriceBreakdown breakdown = policy.quoteBreakdown(route(1));
        assertThat(breakdown.distanceKm()).isEqualByComparingTo("0.001");
        assertThat(breakdown.chargeableKm()).isEqualByComparingTo("0");
        assertThat(breakdown.extraCharge()).isEqualByComparingTo("0.00");
        assertThat(breakdown.total().amount()).isEqualByComparingTo("6.00");
    }

    @Test
    void distanceBelowIncludedChargesOnlyTheBaseFare() {
        PriceBreakdown breakdown = policy.quoteBreakdown(route(2999));

        assertThat(breakdown.distanceKm()).isEqualByComparingTo("2.999");
        assertThat(breakdown.chargeableKm()).isEqualByComparingTo("0");
        assertThat(breakdown.total().amount()).isEqualByComparingTo("6.00");
    }

    @Test
    void distanceExactlyAtIncludedChargesOnlyTheBaseFare() {
        PriceBreakdown breakdown = policy.quoteBreakdown(route(3000));

        assertThat(breakdown.chargeableKm()).isEqualByComparingTo("0");
        assertThat(breakdown.total().amount()).isEqualByComparingTo("6.00");
    }

    @Test
    void distanceJustAboveIncludedChargesOnlyTheExcess() {
        PriceBreakdown breakdown = policy.quoteBreakdown(route(3001));

        assertThat(breakdown.chargeableKm()).isEqualByComparingTo("0.001");
        // 0.001 km × 1.20 = 0.0012 → 0.00 at scale 2 HALF_UP
        assertThat(breakdown.extraCharge()).isEqualByComparingTo("0.00");
        assertThat(breakdown.total().amount()).isEqualByComparingTo("6.00");

        PriceBreakdown fiveHundredMetersOver = policy.quoteBreakdown(route(3500));
        assertThat(fiveHundredMetersOver.chargeableKm()).isEqualByComparingTo("0.500");
        assertThat(fiveHundredMetersOver.extraCharge()).isEqualByComparingTo("0.60");
        assertThat(fiveHundredMetersOver.total().amount()).isEqualByComparingTo("6.60");
    }

    @Test
    void longRouteChargesFullExcessDistance() {
        PriceBreakdown breakdown = policy.quoteBreakdown(route(120_000));

        assertThat(breakdown.chargeableKm()).isEqualByComparingTo("117.000");
        assertThat(breakdown.extraCharge()).isEqualByComparingTo("140.40");
        assertThat(breakdown.total().amount()).isEqualByComparingTo("146.40");
    }

    @Test
    void decimalDistanceRoundsKilometersAtScaleThreeHalfUp() {
        // 18_507 m → 18.507 km; chargeable 15.507; 15.507 × 1.20 = 18.6084 → 18.61
        PriceBreakdown breakdown = policy.quoteBreakdown(route(18_507));

        assertThat(breakdown.distanceKm()).isEqualByComparingTo("18.507");
        assertThat(breakdown.extraCharge()).isEqualByComparingTo("18.61");
        assertThat(breakdown.total().amount()).isEqualByComparingTo("24.61");
    }

    @Test
    void extraChargeRoundingBoundarySitsAtHalfACent() {
        // perKm 1.00: chargeable 0.004 km → 0.004 → 0.00 ; 0.005 km → 0.01 (HALF_UP)
        PricingPolicy perYuan = new PricingPolicy(
            new BigDecimal("6.00"), new BigDecimal("3.0"), new BigDecimal("1.00"),
            BigDecimal.ZERO, "CNY");

        assertThat(perYuan.quoteBreakdown(route(3004)).extraCharge()).isEqualByComparingTo("0.00");
        assertThat(perYuan.quoteBreakdown(route(3005)).extraCharge()).isEqualByComparingTo("0.01");
        assertThat(perYuan.quoteBreakdown(route(3005)).total().amount()).isEqualByComparingTo("6.01");
    }

    @Test
    void minimumFareFloorsTheTotal() {
        PricingPolicy floored = new PricingPolicy(
            new BigDecimal("2.00"), new BigDecimal("0"), new BigDecimal("1.00"),
            new BigDecimal("8.00"), "CNY");

        // 2.00 + 3.0 = 5.00 < 8.00 floor
        assertThat(floored.quoteBreakdown(route(3000)).total().amount()).isEqualByComparingTo("8.00");
        // Beyond the floor the formula wins: 2.00 + 10.0 = 12.00
        assertThat(floored.quoteBreakdown(route(10_000)).total().amount()).isEqualByComparingTo("12.00");
    }

    @Test
    void negativeOrBlankConfigurationIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PricingPolicy(
            new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, "CNY"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PricingPolicy(
            BigDecimal.ONE, new BigDecimal("-3"), BigDecimal.ONE, BigDecimal.ZERO, "CNY"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PricingPolicy(
            BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("-0.01"), BigDecimal.ZERO, "CNY"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PricingPolicy(
            BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("-5"), "CNY"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PricingPolicy(
            BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, " "));
    }

    @Test
    void breakdownTotalAlwaysEqualsTheQuotedPrice() {
        for (int meters : new int[] {1, 2999, 3000, 3001, 8400, 18507, 120_000}) {
            PriceBreakdown breakdown = policy.quoteBreakdown(route(meters));
            assertThat(policy.quote(route(meters))).isEqualTo(breakdown.total());
            // total = max(minFare, base + extra) — recomposed from the displayed components.
            BigDecimal recomposed = breakdown.baseFare().add(breakdown.extraCharge()).max(new BigDecimal("6.00"));
            assertThat(breakdown.total().amount()).isEqualByComparingTo(recomposed);
        }
    }
}
