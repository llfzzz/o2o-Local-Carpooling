package com.o2o.carpooling.common.domain;

import java.time.Instant;
import java.util.Map;

public record AuditLog(
    String auditId,
    String actorId,
    String action,
    String targetType,
    String targetId,
    Map<String, String> metadata,
    Instant occurredAt
) {
    public AuditLog {
        metadata = Map.copyOf(metadata);
    }
}
