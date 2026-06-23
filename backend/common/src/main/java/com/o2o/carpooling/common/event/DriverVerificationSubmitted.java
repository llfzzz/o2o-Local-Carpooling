package com.o2o.carpooling.common.event;

import java.time.Instant;

public record DriverVerificationSubmitted(String caseId, String userId, Instant occurredAt) {
}
