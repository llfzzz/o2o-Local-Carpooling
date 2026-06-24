package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("audit_logs")
class MongoAuditLogDocument {

    @Id
    private String auditId;
    private String actorId;
    private String action;
    private String targetType;
    private String targetId;
    private Map<String, String> metadata;
    private String traceId;
    private Instant occurredAt;

    static MongoAuditLogDocument from(AuditLog log) {
        MongoAuditLogDocument document = new MongoAuditLogDocument();
        document.auditId = log.auditId();
        document.actorId = log.actorId();
        document.action = log.action();
        document.targetType = log.targetType();
        document.targetId = log.targetId();
        document.metadata = log.metadata();
        document.traceId = log.traceId();
        document.occurredAt = log.occurredAt();
        return document;
    }

    AuditLog toDomain() {
        return new AuditLog(auditId, actorId, action, targetType, targetId, metadata == null ? Map.of() : metadata, traceId, occurredAt);
    }
}
