package com.o2o.carpooling.driver;

import java.time.Instant;

record DriverAuditOutboxEntry(
    String eventId,
    String auditId,
    String actorId,
    String action,
    String targetType,
    String targetId,
    String metadataJson,
    int attempts,
    Instant nextAttemptAt
) {
}
