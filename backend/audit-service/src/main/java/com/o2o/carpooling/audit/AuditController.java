package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;
import com.o2o.carpooling.common.foundation.TraceIdFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/audits")
class AuditController {

    private final AuditService auditService;

    AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping
    AuditLog appendRoot(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId,
        @RequestBody AppendAuditRequest request
    ) {
        return append(currentUserId, traceId, request);
    }

    @PostMapping("/logs")
    AuditLog append(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId,
        @RequestBody AppendAuditRequest request
    ) {
        return auditService.append(new AppendAuditCommand(
            StringUtils.hasText(currentUserId) ? currentUserId : request.actorId(),
            request.action(),
            request.targetType(),
            request.targetId(),
            request.metadata(),
            traceId
        ));
    }

    @GetMapping
    AuditLogPage query(
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) String targetId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String actorId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return auditService.query(new AuditQuery(
            Optional.ofNullable(targetType),
            Optional.ofNullable(targetId),
            Optional.ofNullable(action),
            Optional.ofNullable(actorId),
            page,
            size
        ));
    }

    record AppendAuditRequest(String actorId, String action, String targetType, String targetId, Map<String, String> metadata) {
    }
}
