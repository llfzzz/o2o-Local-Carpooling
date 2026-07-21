package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.PriceBreakdown;
import com.o2o.carpooling.common.domain.PricingPolicy;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Rider-facing route confirmation: one authoritative map-service route quote plus the per-seat
 * fare from the SAME PricingPolicy used at trip publish. This is the single pricing authority —
 * the client displays the returned breakdown verbatim and never computes a price.
 *
 * <p>Rate-limited per user because trip-service→map-service Feign bypasses the Gateway's map
 * quota bucket; map-service's route cache and single-flight absorb the rest.
 */
@Service
class RoutePreviewService {

    private static final int PREVIEWS_PER_WINDOW = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MapClient mapClient;
    private final TripMatchingProperties properties;
    private final FixedWindowRateLimiter rateLimiter;

    RoutePreviewService(MapClient mapClient, TripMatchingProperties properties, FixedWindowRateLimiter rateLimiter) {
        this.mapClient = mapClient;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    RoutePreview preview(String userId, LocationRef origin, LocationRef destination) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "authentication is required");
        }
        requireResolved(origin, "origin");
        requireResolved(destination, "destination");
        if (!rateLimiter.allow("trip:route-preview:" + userId, PREVIEWS_PER_WINDOW, WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "ROUTE_PREVIEW_RATE_LIMITED",
                "路线试算过于频繁，请稍后再试");
        }
        RouteSnapshot route = mapClient.quoteRoute(origin, destination);
        PricingPolicy policy = new PricingPolicy(
            properties.getPricing().getBaseFare(),
            properties.getPricing().getIncludedKm(),
            properties.getPricing().getPerKmFare(),
            properties.getPricing().getMinFare(),
            properties.getPricing().getCurrency());
        PriceBreakdown pricing = policy.quoteBreakdown(route);
        return new RoutePreview(route, pricing);
    }

    private void requireResolved(LocationRef location, String name) {
        if (location == null || location.point() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ROUTE_PREVIEW_LOCATION_REQUIRED",
                name + " must be a resolved location");
        }
    }

    record RoutePreview(RouteSnapshot route, PriceBreakdown pricing) {
    }
}
