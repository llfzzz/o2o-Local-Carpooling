package com.o2o.carpooling.identity;

import com.o2o.carpooling.common.domain.IdentityVerificationStatus;
import com.o2o.carpooling.common.domain.LivenessCheckStatus;

import java.time.Instant;

/** The authoritative record of one real-name identity verification session. */
public record IdentityVerification(
    String verificationId,
    String userId,
    IdentityVerificationStatus status,
    LivenessCheckStatus livenessStatus,
    String provider,
    String providerRef,
    Instant createdAt,
    Instant updatedAt
) {
}
