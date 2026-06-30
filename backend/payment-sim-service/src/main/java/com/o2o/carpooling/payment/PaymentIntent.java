package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PaymentIntentStatus;

import java.time.Instant;

/** A payment intent: the authoritative record of one attempt to pay for an order. */
public record PaymentIntent(
    String intentId,
    String orderId,
    String riderId,
    Money amount,
    PaymentIntentStatus status,
    String provider,
    String providerRef,
    Instant createdAt,
    Instant updatedAt
) {
}
