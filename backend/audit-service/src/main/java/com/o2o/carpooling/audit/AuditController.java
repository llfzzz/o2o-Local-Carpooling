package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audits")
class AuditController {

    @PostMapping
    AuditLog appendRoot(@RequestBody AppendAuditRequest request) {
        return append(request);
    }

    @PostMapping("/logs")
    AuditLog append(@RequestBody AppendAuditRequest request) {
        return new AuditLog(
            "audit-" + UUID.randomUUID(),
            request.actorId(),
            request.action(),
            request.targetType(),
            request.targetId(),
            request.metadata(),
            Instant.now()
        );
    }

    record AppendAuditRequest(String actorId, String action, String targetType, String targetId, Map<String, String> metadata) {
    }
}
