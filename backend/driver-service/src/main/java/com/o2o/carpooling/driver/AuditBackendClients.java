package com.o2o.carpooling.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "audit-service", contextId = "driverAuditFeignClient")
interface DriverAuditFeignClient {
    @PostMapping("/api/audits/logs")
    void append(@RequestBody AuditAppendRequest request);
}

/**
 * Writes audit events into the service-local outbox (committed in the same
 * transaction as the driver status change). A scheduled relay
 * ({@link DriverAuditOutboxPublisher}) delivers them to the audit service with
 * retry, so audits are not lost on a transient audit-service outage.
 */
@Component
class OutboxAuditClient implements AuditClient {

    private final DriverAuditOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    OutboxAuditClient(DriverAuditOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void append(String actorId, String action, String targetType, String targetId, Map<String, String> metadata) {
        outboxRepository.enqueue(
            "audit-" + UUID.randomUUID(),
            actorId,
            action,
            targetType,
            targetId,
            writeJson(metadata),
            Instant.now()
        );
    }

    private String writeJson(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception exception) {
            throw new IllegalArgumentException("failed to serialize audit metadata", exception);
        }
    }
}

record AuditAppendRequest(String actorId, String action, String targetType, String targetId, Map<String, String> metadata) {
}
