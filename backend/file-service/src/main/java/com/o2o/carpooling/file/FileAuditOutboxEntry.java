package com.o2o.carpooling.file;

import java.time.Instant;

record FileAuditOutboxEntry(
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
