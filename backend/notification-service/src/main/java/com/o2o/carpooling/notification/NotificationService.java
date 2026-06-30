package com.o2o.carpooling.notification;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int PREVIEW_MAX = 255;
    private static final int INBOX_MAX = 100;

    private final List<NotificationChannelAdapter> adapters;
    private final NotificationDeliveryRepository repository;
    private final Clock clock;

    NotificationService(List<NotificationChannelAdapter> adapters, NotificationDeliveryRepository repository, Clock clock) {
        this.adapters = adapters;
        this.repository = repository;
        this.clock = clock;
    }

    /** Current user's inbox, newest first. */
    List<DeliveryRecord> listInbox(String userId, int limit) {
        requireUser(userId);
        int clamped = Math.min(Math.max(limit, 1), INBOX_MAX);
        return repository.findByUserId(userId, clamped);
    }

    /**
     * Reveal the sensitive payload of a delivery the user owns, if still within its TTL.
     * Audited (actor + delivery id + timestamp only — never the value).
     */
    String reveal(String userId, String deliveryId) {
        requireUser(userId);
        String payload = repository.findRevealablePayload(deliveryId, userId, clock.instant())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "DEMO_REVEAL_UNAVAILABLE",
                "nothing to reveal for this delivery (unknown, not owned, or expired)"));
        log.info("demo.inbox.reveal actor={} deliveryId={} at={}", userId, deliveryId, clock.instant());
        return payload;
    }

    boolean markRead(String userId, String deliveryId) {
        requireUser(userId);
        return repository.markRead(deliveryId, userId, clock.instant()) > 0;
    }

    /** Operator demo control: simulate a delivery outcome (delivered/failed/retried/read). */
    void simulateStatus(String actorId, String deliveryId, DeliveryStatus status) {
        boolean incrementRetry = status == DeliveryStatus.RETRYING;
        int updated = repository.updateStatusByDeliveryId(deliveryId, status, incrementRetry, clock.instant());
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "delivery not found: " + deliveryId);
        }
        log.info("demo.control.notification actor={} deliveryId={} status={} at={}", actorId, deliveryId, status, clock.instant());
    }

    private void requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "missing authenticated user");
        }
    }

    DeliveryReceipt notify(NotificationMessage message) {
        validate(message);
        NotificationChannelAdapter adapter = adapters.stream()
            .filter(candidate -> candidate.supports(message.channel()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NOTIFICATION_PROVIDER_UNCONFIGURED",
                "no notification channel adapter configured for " + message.channel()));

        DeliveryStatus status = adapter.send(message);
        Instant now = clock.instant();
        Instant revealExpiresAt = message.revealTtl() == null ? null : now.plus(message.revealTtl());
        DeliveryRecord record = new DeliveryRecord(
            "ntf-" + UUID.randomUUID(),
            message.userId(),
            message.channel(),
            message.category(),
            message.title(),
            maskPreview(message.body(), message.revealablePayload()),
            status,
            message.correlationId(),
            0,
            now,
            now,
            null
        );
        repository.save(record, message.revealablePayload(), revealExpiresAt);
        return new DeliveryReceipt(record.deliveryId(), record.channel(), record.status(), record.createdAt());
    }

    private void validate(NotificationMessage message) {
        if (message == null || message.channel() == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (!StringUtils.hasText(message.userId())) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(message.category())) {
            throw new IllegalArgumentException("category is required");
        }
        if (!StringUtils.hasText(message.title())) {
            throw new IllegalArgumentException("title is required");
        }
        if (!StringUtils.hasText(message.body())) {
            throw new IllegalArgumentException("body is required");
        }
    }

    /** Masks the revealable payload inside the body and truncates to a safe preview length. */
    private String maskPreview(String body, String revealablePayload) {
        String preview = body;
        if (StringUtils.hasText(revealablePayload)) {
            String mask = "•".repeat(Math.min(Math.max(revealablePayload.length(), 4), 8));
            preview = preview.replace(revealablePayload, mask);
        }
        return preview.length() > PREVIEW_MAX ? preview.substring(0, PREVIEW_MAX) : preview;
    }
}
