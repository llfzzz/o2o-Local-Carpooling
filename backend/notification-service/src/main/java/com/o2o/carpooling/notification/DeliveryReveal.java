package com.o2o.carpooling.notification;

import java.time.Instant;

/**
 * Internal view of the latest delivery for a (user, category), including the revealable value
 * when still within its TTL. Used service-to-service (e.g. auth's demo login-code peek) and is
 * never exposed through the Gateway.
 */
public record DeliveryReveal(
    String deliveryId,
    String maskedPreview,
    String value,
    Instant expiresAt,
    Instant createdAt
) {
}
