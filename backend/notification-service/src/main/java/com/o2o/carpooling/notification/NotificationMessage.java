package com.o2o.carpooling.notification;

import java.time.Duration;

/**
 * A notification to deliver to one recipient over one channel.
 *
 * @param userId            recipient; scopes the inbox
 * @param channel           SMS / PUSH / IN_APP
 * @param category          machine category, e.g. ORDER_PAID, IDENTITY_VERIFICATION_RESULT
 * @param title             short human title
 * @param body              full human body; sensitive parts are masked before storage
 * @param revealablePayload sensitive value shown only via an explicit, short-lived reveal (nullable)
 * @param revealTtl         how long {@code revealablePayload} stays revealable (nullable = no expiry)
 * @param correlationId     trace/correlation id linking this delivery to a business flow (nullable)
 * @param linkType          related business object type: ORDER / TRIP / PAYMENT / CONVERSATION (nullable)
 * @param linkId            id of the related business object (nullable)
 * @param dedupeKey         sender-supplied idempotency key; a repeat notify with the same key is a
 *                          no-op returning the original receipt (nullable)
 */
public record NotificationMessage(
    String userId,
    ChannelType channel,
    String category,
    String title,
    String body,
    String revealablePayload,
    Duration revealTtl,
    String correlationId,
    String linkType,
    String linkId,
    String dedupeKey
) {

    /** Convenience for callers without link/dedupe metadata (existing call sites and tests). */
    public NotificationMessage(
        String userId,
        ChannelType channel,
        String category,
        String title,
        String body,
        String revealablePayload,
        Duration revealTtl,
        String correlationId
    ) {
        this(userId, channel, category, title, body, revealablePayload, revealTtl, correlationId, null, null, null);
    }
}
