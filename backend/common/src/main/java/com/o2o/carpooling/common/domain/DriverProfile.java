package com.o2o.carpooling.common.domain;

import java.time.Instant;

public record DriverProfile(
    String driverId,
    String userId,
    DriverVerificationStatus status,
    Vehicle vehicle,
    Instant updatedAt
) {
}
