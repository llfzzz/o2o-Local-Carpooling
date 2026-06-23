package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingPolicyTest {

    @Test
    void pricesRouteByBaseFareAndDistance() {
        PricingPolicy policy = new PricingPolicy(new BigDecimal("6.00"), new BigDecimal("1.20"));

        Money price = policy.quote(new RouteSnapshot("route-1", 18500, 2100, "mock-amap-v1"));

        assertThat(price.amount()).isEqualByComparingTo("28.20");
        assertThat(price.currency()).isEqualTo("CNY");
    }
}
