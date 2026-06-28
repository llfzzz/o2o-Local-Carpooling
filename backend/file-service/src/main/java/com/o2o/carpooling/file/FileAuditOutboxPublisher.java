package com.o2o.carpooling.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Relays pending file audit-outbox entries to the audit service with retry.
 * On delivery failure the entry stays PENDING with an incremented attempt count
 * and a backed-off next_attempt_at, giving at-least-once audit delivery.
 */
@Service
class FileAuditOutboxPublisher {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    private static final int BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(FileAuditOutboxPublisher.class);

    private final FileAuditOutboxRepository repository;
    private final FileAuditFeignClient auditFeignClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    FileAuditOutboxPublisher(FileAuditOutboxRepository repository, FileAuditFeignClient auditFeignClient) {
        this.repository = repository;
        this.auditFeignClient = auditFeignClient;
    }

    @Scheduled(fixedDelayString = "${files.audit-outbox.publish-fixed-delay:PT10S}")
    void publishDue() {
        publishDue(Instant.now());
    }

    int publishDue(Instant now) {
        int sent = 0;
        for (FileAuditOutboxEntry entry : repository.findSendable(now, BATCH_SIZE)) {
            try {
                auditFeignClient.append(new AuditAppendRequest(
                    entry.actorId(),
                    entry.action(),
                    entry.targetType(),
                    entry.targetId(),
                    readMetadata(entry.metadataJson())
                ));
                repository.markSent(entry.eventId(), now);
                sent++;
            } catch (RuntimeException exception) {
                log.warn("Audit outbox delivery failed eventId={} action={} attempts={}", entry.eventId(), entry.action(), entry.attempts(), exception);
                repository.markFailed(entry.eventId(), exception.getMessage(), now.plus(RETRY_DELAY), now);
            }
        }
        return sent;
    }

    private Map<String, String> readMetadata(String json) {
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
