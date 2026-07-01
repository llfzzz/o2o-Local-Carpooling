package com.o2o.carpooling.identity;

import com.o2o.carpooling.common.domain.IdentityVerificationStateMachine;
import com.o2o.carpooling.common.domain.IdentityVerificationStatus;
import com.o2o.carpooling.common.domain.LivenessCheckStateMachine;
import com.o2o.carpooling.common.domain.LivenessCheckStatus;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative writer for identity verification sessions. Starts sessions in PENDING (the outcome
 * is never decided at start time), exposes them for polling, and applies operator/provider-driven
 * outcomes through the two state machines. Every terminal (or retry) outcome is delivered
 * asynchronously to the user's Demo inbox — results are never returned inline. Approval requires a
 * passed liveness check.
 */
@Service
class IdentityVerificationService {

    private static final String RESULT_CATEGORY = "IDENTITY_VERIFICATION_RESULT";

    private static final Map<IdentityVerificationStatus, String> STATUS_LABEL = Map.of(
        IdentityVerificationStatus.APPROVED, "实名认证通过",
        IdentityVerificationStatus.REJECTED, "实名认证被驳回",
        IdentityVerificationStatus.TIMEOUT, "实名认证超时",
        IdentityVerificationStatus.RETRY_REQUIRED, "实名认证需要重试");

    private final IdentityVerificationRepository repository;
    private final List<IdentityVerificationProvider> providers;
    private final ProviderProperties providerProperties;
    private final NotificationFeignClient notification;
    private final Clock clock;
    private final IdentityVerificationStateMachine sessionStateMachine = new IdentityVerificationStateMachine();
    private final LivenessCheckStateMachine livenessStateMachine = new LivenessCheckStateMachine();

    IdentityVerificationService(
        IdentityVerificationRepository repository,
        List<IdentityVerificationProvider> providers,
        ProviderProperties providerProperties,
        NotificationFeignClient notification,
        Clock clock
    ) {
        this.repository = repository;
        this.providers = providers;
        this.providerProperties = providerProperties;
        this.notification = notification;
        this.clock = clock;
    }

    @Transactional
    IdentityVerification start(String currentUserId, String realName, String idNumber, String idempotencyKey) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "IDENTITY_USER_REQUIRED", "an authenticated user is required");
        }
        String key = StringUtils.hasText(idempotencyKey) ? idempotencyKey : "idv-" + currentUserId;
        return repository.findByUserIdAndIdempotencyKey(currentUserId, key)
            .orElseGet(() -> create(currentUserId, realName, idNumber, key));
    }

    IdentityVerification get(String verificationId, String currentUserId) {
        IdentityVerification verification = requireExists(verificationId);
        // Owner-scoped read: a user can only see their own verification session.
        if (StringUtils.hasText(currentUserId) && !currentUserId.equals(verification.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "IDENTITY_FORBIDDEN", "not allowed to view this verification");
        }
        return verification;
    }

    /** Apply an operator/provider-driven session outcome; delivers the result to the inbox. */
    @Transactional
    IdentityVerification applySessionOutcome(String verificationId, IdentityVerificationStatus target) {
        IdentityVerification current = requireExists(verificationId);
        // The state machine is the authoritative guard first; the liveness rule is an extra
        // constraint on an otherwise-legal APPROVED transition (only reachable from PENDING).
        if (!sessionStateMachine.canTransition(current.status(), target)) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDENTITY_ILLEGAL_TRANSITION",
                "cannot transition session from " + current.status() + " to " + target);
        }
        if (target == IdentityVerificationStatus.APPROVED && current.livenessStatus() != LivenessCheckStatus.PASSED) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDENTITY_LIVENESS_REQUIRED",
                "approval requires a passed liveness check");
        }
        boolean moved = repository.transitionSession(verificationId, current.status(), target, clock.instant());
        IdentityVerification updated = requireExists(verificationId);
        // A retry restart (back to PENDING) is not a result; every other outcome is delivered.
        if (moved && target != IdentityVerificationStatus.PENDING) {
            deliverResult(updated, target);
        }
        return updated;
    }

    /** Apply an operator/provider-driven liveness outcome through the liveness state machine. */
    @Transactional
    IdentityVerification applyLivenessOutcome(String verificationId, LivenessCheckStatus target) {
        IdentityVerification current = requireExists(verificationId);
        if (!livenessStateMachine.canTransition(current.livenessStatus(), target)) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDENTITY_ILLEGAL_TRANSITION",
                "cannot transition liveness from " + current.livenessStatus() + " to " + target);
        }
        repository.transitionLiveness(verificationId, current.livenessStatus(), target, clock.instant());
        return requireExists(verificationId);
    }

    IdentityVerification requireExists(String verificationId) {
        return repository.findByVerificationId(verificationId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "IDENTITY_VERIFICATION_NOT_FOUND",
                "identity verification not found: " + verificationId));
    }

    private IdentityVerification create(String currentUserId, String realName, String idNumber, String idempotencyKey) {
        IdentityVerificationProvider provider = provider();
        String verificationId = "idv-" + UUID.randomUUID();
        IdentityVerificationProvider.ProviderVerification init = provider.start(
            new IdentityVerificationProvider.StartVerificationCommand(verificationId, currentUserId, realName, mask(idNumber)));
        Instant now = clock.instant();
        IdentityVerification verification = new IdentityVerification(
            verificationId, currentUserId, init.status(), init.livenessStatus(), provider.name(), init.providerRef(), now, now);
        repository.save(verification, idempotencyKey);
        return verification;
    }

    private void deliverResult(IdentityVerification verification, IdentityVerificationStatus outcome) {
        notification.notify(new NotificationFeignClient.NotifyRequest(
            verification.userId(),
            "IN_APP",
            RESULT_CATEGORY,
            "实名认证结果",
            "您的实名认证结果已更新，请在收件箱查看。",
            STATUS_LABEL.getOrDefault(outcome, outcome.name()),
            3600L,
            verification.verificationId()));
    }

    private IdentityVerificationProvider provider() {
        String type = providerProperties.getIdentity().getType();
        return providers.stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(type))
            .findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_PROVIDER_UNCONFIGURED",
                "no identity provider configured for type '" + type + "'"));
    }

    /** Mask the national id so PII never lands in storage, provider commands, or logs. */
    private String mask(String idNumber) {
        if (!StringUtils.hasText(idNumber)) {
            return "";
        }
        String trimmed = idNumber.trim();
        if (trimmed.length() <= 2) {
            return "*".repeat(trimmed.length());
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 2) + trimmed.charAt(trimmed.length() - 1);
    }
}
