package com.o2o.carpooling.common.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative payment-intent transitions. The demo provider and any future real provider must
 * route every status change through here so illegal transitions (e.g. resurrecting a terminal
 * intent, or capturing without payment) are rejected consistently.
 */
public final class PaymentIntentStateMachine {

    private static final Map<PaymentIntentStatus, Set<PaymentIntentStatus>> ALLOWED =
        new EnumMap<>(PaymentIntentStatus.class);

    static {
        ALLOWED.put(PaymentIntentStatus.REQUIRES_PAYMENT, EnumSet.of(
            PaymentIntentStatus.AUTHORIZED,
            PaymentIntentStatus.SUCCEEDED,
            PaymentIntentStatus.FAILED,
            PaymentIntentStatus.CANCELED,
            PaymentIntentStatus.EXPIRED));
        ALLOWED.put(PaymentIntentStatus.AUTHORIZED, EnumSet.of(
            PaymentIntentStatus.SUCCEEDED,
            PaymentIntentStatus.FAILED,
            PaymentIntentStatus.CANCELED,
            PaymentIntentStatus.EXPIRED));
        ALLOWED.put(PaymentIntentStatus.SUCCEEDED, EnumSet.noneOf(PaymentIntentStatus.class));
        ALLOWED.put(PaymentIntentStatus.FAILED, EnumSet.noneOf(PaymentIntentStatus.class));
        ALLOWED.put(PaymentIntentStatus.CANCELED, EnumSet.noneOf(PaymentIntentStatus.class));
        ALLOWED.put(PaymentIntentStatus.EXPIRED, EnumSet.noneOf(PaymentIntentStatus.class));
    }

    public boolean canTransition(PaymentIntentStatus from, PaymentIntentStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(PaymentIntentStatus.class)).contains(to);
    }

    /** Validate a transition, throwing if illegal. Returns the target status for chaining. */
    public PaymentIntentStatus require(PaymentIntentStatus from, PaymentIntentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("payment intent cannot transition from " + from + " to " + to);
        }
        return to;
    }
}
