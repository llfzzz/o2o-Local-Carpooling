package com.o2o.carpooling.notification;

import java.time.Instant;

/**
 * Inbox-safe view of a delivery. Intentionally omits the revealable payload — the sensitive
 * value is only returned by the explicit reveal endpoint, never in listings.
 *
 * <p>{@code cursor} is the row's monotonic numeric id, used for keyset pagination (0 before the
 * row is persisted). {@code linkType}/{@code linkId} point at the related business object
 * (ORDER / TRIP / PAYMENT / CONVERSATION) so the client can deep-link from the message.
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
    Instant readAt,
    String linkType,
    String linkId,
    long cursor,
    boolean revealable
) {
}
