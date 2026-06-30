package com.o2o.carpooling.notification;

import java.time.Duration;

/**
 * A notification to deliver to one recipient over one channel.
 *
 * @param userId            recipient; scopes the Demo Inbox
 * @param channel           SMS / PUSH / IN_APP
 * @param category          machine category, e.g. AUTH_SMS_CODE, PAYMENT_STATUS, REVIEW_INVITATION
 * @param title             short human title
 * @param body              full human body; sensitive parts are masked before storage
 * @param revealablePayload sensitive value shown only via an explicit, short-lived reveal (nullable)
 * @param revealTtl         how long {@code revealablePayload} stays revealable (nullable = no expiry)
 * @param correlationId     trace/correlation id linking this delivery to a business flow (nullable)
 */
public record NotificationMessage(
    String userId,
    ChannelType channel,
    String category,
    String title,
    String body,
    String revealablePayload,
    Duration revealTtl,
    String correlationId
) {
}
