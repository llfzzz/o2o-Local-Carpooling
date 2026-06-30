package com.o2o.carpooling.order;

import java.time.Instant;

record OrderAuditOutboxEntry(
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
