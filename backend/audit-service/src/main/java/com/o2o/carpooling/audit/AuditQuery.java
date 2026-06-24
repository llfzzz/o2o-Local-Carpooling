package com.o2o.carpooling.audit;

import java.util.Optional;

record AuditQuery(
    Optional<String> targetType,
    Optional<String> targetId,
    Optional<String> action,
    Optional<String> actorId,
    int page,
    int size
) {
}
