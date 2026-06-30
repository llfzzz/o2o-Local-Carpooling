package com.o2o.carpooling.notification;

import java.time.Instant;

/**
 * Inbox-safe view of a delivery. Intentionally omits the revealable payload — the sensitive
 * value is only returned by the explicit reveal endpoint, never in listings.
 */
public record DeliveryRecord(
    String deliveryId,
    String userId,
    ChannelType channel,
    String category,
    String title,
    String maskedPreview,
    DeliveryStatus status,
    String correlationId,
    int retryCount,
    Instant createdAt,
    Instant updatedAt,
    Instant readAt
) {
}
