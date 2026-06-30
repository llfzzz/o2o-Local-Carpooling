package com.o2o.carpooling.notification;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class NotificationService {

    private static final int PREVIEW_MAX = 255;

    private final List<NotificationChannelAdapter> adapters;
    private final NotificationDeliveryRepository repository;
    private final Clock clock;

    NotificationService(List<NotificationChannelAdapter> adapters, NotificationDeliveryRepository repository, Clock clock) {
        this.adapters = adapters;
        this.repository = repository;
        this.clock = clock;
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
