package com.o2o.carpooling.common.domain;

/**
 * Overall status of a real-name identity verification session. Modeled to match a real
 * identity/KYC provider so the demo provider can be swapped for a real one without changing the
 * flow. Terminal states: APPROVED, REJECTED, TIMEOUT. RETRY_REQUIRED is a non-terminal signal that
 * the user must start over (it loops back to PENDING).
 */
public enum IdentityVerificationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    TIMEOUT,
    RETRY_REQUIRED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == TIMEOUT;
    }
}
