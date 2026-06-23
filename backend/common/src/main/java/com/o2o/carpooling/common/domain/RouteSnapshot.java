package com.o2o.carpooling.common.domain;

public record RouteSnapshot(String routeId, int distanceMeters, int durationSeconds, String providerTrace) {
    public RouteSnapshot {
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required");
        }
        if (distanceMeters <= 0) {
            throw new IllegalArgumentException("distanceMeters must be positive");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (providerTrace == null || providerTrace.isBlank()) {
            throw new IllegalArgumentException("providerTrace is required");
        }
    }
}
