package com.o2o.carpooling.audit;

import java.util.Map;

record AppendAuditCommand(
    String actorId,
    String action,
    String targetType,
    String targetId,
    Map<String, String> metadata,
    String traceId
) {
}
