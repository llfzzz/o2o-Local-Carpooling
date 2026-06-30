package com.o2o.carpooling.common.domain;

/**
 * Lifecycle of a payment intent, modeled to match real PSP semantics so a real provider can
 * replace the demo one without changing the order flow. Terminal states: SUCCEEDED, FAILED,
 * CANCELED, EXPIRED.
 */
public enum PaymentIntentStatus {
    REQUIRES_PAYMENT,
    AUTHORIZED,
    SUCCEEDED,
    FAILED,
    CANCELED,
    EXPIRED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == EXPIRED;
    }
}
