package com.o2o.carpooling.common.domain;

/**
 * Liveness (anti-spoofing) sub-check status within an identity verification session. Terminal
 * states: PASSED, FAILED, TIMEOUT. RETRY_REQUIRED loops back to PENDING for another attempt.
 * Approving a session requires liveness to have PASSED.
 */
public enum LivenessCheckStatus {
    PENDING,
    PASSED,
    FAILED,
    TIMEOUT,
    RETRY_REQUIRED;

    public boolean isTerminal() {
        return this == PASSED || this == FAILED || this == TIMEOUT;
    }
}
