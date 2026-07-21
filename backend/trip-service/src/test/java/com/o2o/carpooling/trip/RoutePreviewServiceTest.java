package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.PricingPolicy;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.InMemoryFixedWindowRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RoutePreviewServiceTest {

    private final TripMatchingProperties properties = new TripMatchingProperties();
    private final RouteSnapshot route = new RouteSnapshot("route-1", 18_500, 2_100, "amap-v5");
    private final RoutePreviewService service = new RoutePreviewService(
        new MapClient() {
            @Override
            public RouteSnapshot quoteRoute(String origin, String destination, String city) {
                throw new UnsupportedOperationException("preview must use the structured form");
            }

            @Override
            public RouteSnapshot quoteRoute(LocationRef origin, LocationRef destination) {
                return route;
            }

            @Override
            public java.util.List<LocationRef> demoPlaces(String cityCode) {
                return java.util.List.of();
            }
        },
        properties,
        new InMemoryFixedWindowRateLimiter(Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC))
    );

    private LocationRef place(double lat, double lng) {
        return new LocationRef(new GeoPoint(lat, lng, CoordinateDatum.GCJ02), "amap", "poi-1", "0592",
            "350203", "软件园三期", "厦门市湖里区", LocationSource.AUTOCOMPLETE, null, Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    void previewReturnsRouteAndTheExactPolicyBreakdown() {
        RoutePreviewService.RoutePreview preview = service.preview("user-1", place(24.5, 118.1), place(24.6, 118.0));

        assertThat(preview.route()).isEqualTo(route);
        // Exactly the same policy as publish: same config → identical breakdown and total.
        var expected = new PricingPolicy(
            properties.getPricing().getBaseFare(),
            properties.getPricing().getIncludedKm(),
            properties.getPricing().getPerKmFare(),
            properties.getPricing().getMinFare(),
            properties.getPricing().getCurrency()).quoteBreakdown(route);
        assertThat(preview.pricing()).isEqualTo(expected);
        assertThat(preview.pricing().total().amount()).isEqualByComparingTo("24.60");
    }

    @Test
    void previewRequiresAuthenticationAndResolvedEndpoints() {
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.preview(" ", place(24.5, 118.1), place(24.6, 118.0)))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("AUTH_REQUIRED"));
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.preview("user-1", null, place(24.6, 118.0)))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("ROUTE_PREVIEW_LOCATION_REQUIRED"));
    }

    @Test
    void previewIsRateLimitedPerUser() {
        for (int i = 0; i < 30; i++) {
            service.preview("user-1", place(24.5, 118.1), place(24.6, 118.0));
        }
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.preview("user-1", place(24.5, 118.1), place(24.6, 118.0)))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("ROUTE_PREVIEW_RATE_LIMITED"));
        // Another user's budget is untouched.
        assertThat(service.preview("user-2", place(24.5, 118.1), place(24.6, 118.0)).pricing()).isNotNull();
    }
}
