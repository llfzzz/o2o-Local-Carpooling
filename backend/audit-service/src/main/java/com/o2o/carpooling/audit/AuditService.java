package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
class AuditService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final AuditLogStore auditLogStore;

    AuditService(AuditLogStore auditLogStore) {
        this.auditLogStore = auditLogStore;
    }

    AuditLog append(AppendAuditCommand command) {
        validate(command);
        return auditLogStore.save(new AuditLog(
            "audit-" + UUID.randomUUID(),
            command.actorId().trim(),
            command.action().trim(),
            command.targetType().trim(),
            command.targetId().trim(),
            command.metadata() == null ? Map.of() : command.metadata(),
            command.traceId(),
            Instant.now()
        ));
    }

    AuditLogPage query(AuditQuery query) {
        int page = Math.max(0, query.page());
        int requestedSize = query.size() <= 0 ? DEFAULT_PAGE_SIZE : query.size();
        int size = Math.min(requestedSize, MAX_PAGE_SIZE);
        return auditLogStore.query(new AuditQuery(
            query.targetType(),
            query.targetId(),
            query.action(),
            query.actorId(),
            page,
            size
        ));
    }

    private void validate(AppendAuditCommand command) {
        if (!StringUtils.hasText(command.actorId())) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (!StringUtils.hasText(command.action())) {
            throw new IllegalArgumentException("action is required");
        }
        if (!StringUtils.hasText(command.targetType())) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (!StringUtils.hasText(command.targetId())) {
            throw new IllegalArgumentException("targetId is required");
        }
    }
}
