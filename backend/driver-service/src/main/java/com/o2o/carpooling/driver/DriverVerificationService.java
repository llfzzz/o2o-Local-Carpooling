package com.o2o.carpooling.driver;

import com.o2o.carpooling.common.domain.DriverVerificationStatus;
import com.o2o.carpooling.common.domain.NotificationCategory;
import com.o2o.carpooling.common.domain.VerificationCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
class DriverVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DriverVerificationService.class);

    private final DriverVerificationRepository repository;
    private final AuditClient auditClient;
    private final NotificationFeignClient notificationClient;

    DriverVerificationService(DriverVerificationRepository repository, AuditClient auditClient,
                              NotificationFeignClient notificationClient) {
        this.repository = repository;
        this.auditClient = auditClient;
        this.notificationClient = notificationClient;
    }

    /**
     * Transition a verification case and enqueue its audit event in the SAME
     * transaction, so the status change and the audit record commit atomically.
     * The Message Center notice to the driver is best-effort (mirrors identity-service):
     * a notification outage must never fail the review decision.
     */
    @Transactional
    VerificationCase transition(String caseId, DriverVerificationStatus status, String action, String actorId) {
        repository.updateStatus(caseId, status);
        VerificationCase verificationCase = repository.findByCaseId(caseId)
            .orElseThrow(() -> new IllegalArgumentException("verification case not found: " + caseId));
        auditClient.append(
            StringUtils.hasText(actorId) ? actorId : "operator-local",
            action,
            "DRIVER_VERIFICATION",
            caseId,
            Map.of("driverUserId", verificationCase.userId())
        );
        notifyOutcome(verificationCase, status);
        return verificationCase;
    }

    private void notifyOutcome(VerificationCase verificationCase, DriverVerificationStatus status) {
        String body = switch (status) {
            case APPROVED -> "您的司机证件审核已通过，现在可以发布行程了。";
            case REJECTED -> "您的司机证件审核未通过，请检查证件后重新提交。";
            default -> null;
        };
        if (body == null) {
            return;
        }
        try {
            notificationClient.notify(new NotificationFeignClient.NotifyRequest(
                verificationCase.userId(), "IN_APP", NotificationCategory.DRIVER_VERIFICATION_RESULT.name(),
                "司机审核结果", body, null, null, null, null, null,
                "driver-case:" + verificationCase.caseId() + ":" + status.name()));
        } catch (RuntimeException exception) {
            log.warn("failed to deliver driver verification notice caseId={} (best-effort)",
                verificationCase.caseId(), exception);
        }
    }
}
