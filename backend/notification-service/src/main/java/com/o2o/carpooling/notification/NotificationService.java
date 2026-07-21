package com.o2o.carpooling.notification;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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

    /** Keyset page of the current user's inbox, newest first; optional category filter. */
    List<DeliveryRecord> listInbox(String userId, int limit, Long beforeCursor, String category) {
        requireUser(userId);
        int clamped = Math.min(Math.max(limit, 1), INBOX_MAX);
        return repository.findByUserId(userId, clamped, beforeCursor, category);
    }

    long unreadCount(String userId) {
        requireUser(userId);
        return repository.countUnread(userId);
    }

    /**
     * Reveal the sensitive payload of a delivery the user owns, if still within its TTL.
     * Audited (actor + delivery id + timestamp only — never the value).
     */
    String reveal(String userId, String deliveryId) {
        requireUser(userId);
        String payload = repository.findRevealablePayload(deliveryId, userId, clock.instant())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "REVEAL_UNAVAILABLE",
                "nothing to reveal for this delivery (unknown, not owned, or expired)"));
        log.info("inbox.reveal actor={} deliveryId={} at={}", userId, deliveryId, clock.instant());
        return payload;
    }

    boolean markRead(String userId, String deliveryId) {
        requireUser(userId);
        return repository.markRead(deliveryId, userId, clock.instant()) > 0;
    }

    int markAllRead(String userId) {
        requireUser(userId);
        return repository.markAllRead(userId, clock.instant());
    }

    /** Operator demo control: recent deliveries across users (masked previews only). */
    List<DeliveryRecord> listRecentDeliveries(int limit) {
        return repository.findRecent(Math.min(Math.max(limit, 1), INBOX_MAX));
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
        // Senders relay through at-least-once outboxes: a repeated dedupeKey is a no-op that
        // returns the original receipt instead of creating a duplicate inbox row.
        if (StringUtils.hasText(message.dedupeKey())) {
            var existing = repository.findReceiptByDedupeKey(message.dedupeKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
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
            null,
            message.linkType(),
            message.linkId(),
            0L,
            StringUtils.hasText(message.revealablePayload())
        );
        try {
            repository.save(record, message.revealablePayload(), revealExpiresAt, message.dedupeKey());
        } catch (DuplicateKeyException raced) {
            // Two relays raced on the same dedupeKey; the winner's receipt is the answer.
            return repository.findReceiptByDedupeKey(message.dedupeKey()).orElseThrow(() -> raced);
        }
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
        // Login codes must never become inbox messages: their demo delivery is the
        // challenge-bound login-page peek inside auth-service.
        if ("AUTH_SMS_CODE".equals(message.category())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CATEGORY_NOT_INBOXABLE",
                "login verification codes are not inbox messages");
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
