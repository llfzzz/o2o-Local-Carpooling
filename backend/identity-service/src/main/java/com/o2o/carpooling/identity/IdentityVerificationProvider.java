package com.o2o.carpooling.identity;

import com.o2o.carpooling.common.domain.IdentityVerificationStatus;
import com.o2o.carpooling.common.domain.LivenessCheckStatus;

/**
 * Provider seam for real-name identity verification (incl. liveness). The demo provider starts a
 * session locally and lets an operator drive the outcome; a real KYC vendor implements the same
 * contract and is selected via {@code providers.identity.type} without changing the flow.
 */
interface IdentityVerificationProvider {

    /** Provider key, matched against providers.identity.type. */
    String name();

    ProviderVerification start(StartVerificationCommand command);

    record StartVerificationCommand(String verificationId, String userId, String realName, String idNumberMasked) {
    }

    record ProviderVerification(IdentityVerificationStatus status, LivenessCheckStatus livenessStatus, String providerRef) {
    }
}
