package com.o2o.carpooling.common.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative identity-verification session transitions. Every status change routes through here
 * so illegal moves (e.g. resurrecting a terminal session) are rejected consistently, mirroring the
 * payment-intent state machine. RETRY_REQUIRED loops back to PENDING; terminal states are final.
 */
public final class IdentityVerificationStateMachine {

    private static final Map<IdentityVerificationStatus, Set<IdentityVerificationStatus>> ALLOWED =
        new EnumMap<>(IdentityVerificationStatus.class);

    static {
        ALLOWED.put(IdentityVerificationStatus.PENDING, EnumSet.of(
            IdentityVerificationStatus.APPROVED,
            IdentityVerificationStatus.REJECTED,
            IdentityVerificationStatus.TIMEOUT,
            IdentityVerificationStatus.RETRY_REQUIRED));
        ALLOWED.put(IdentityVerificationStatus.RETRY_REQUIRED, EnumSet.of(
            IdentityVerificationStatus.PENDING));
        ALLOWED.put(IdentityVerificationStatus.APPROVED, EnumSet.noneOf(IdentityVerificationStatus.class));
        ALLOWED.put(IdentityVerificationStatus.REJECTED, EnumSet.noneOf(IdentityVerificationStatus.class));
        ALLOWED.put(IdentityVerificationStatus.TIMEOUT, EnumSet.noneOf(IdentityVerificationStatus.class));
    }

    public boolean canTransition(IdentityVerificationStatus from, IdentityVerificationStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(IdentityVerificationStatus.class)).contains(to);
    }

    public IdentityVerificationStatus require(IdentityVerificationStatus from, IdentityVerificationStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("identity verification cannot transition from " + from + " to " + to);
        }
        return to;
    }
}
