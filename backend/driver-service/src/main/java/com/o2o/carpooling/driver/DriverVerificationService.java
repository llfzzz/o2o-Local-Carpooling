package com.o2o.carpooling.driver;

import com.o2o.carpooling.common.domain.DriverVerificationStatus;
import com.o2o.carpooling.common.domain.VerificationCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
class DriverVerificationService {

    private final DriverVerificationRepository repository;
    private final AuditClient auditClient;

    DriverVerificationService(DriverVerificationRepository repository, AuditClient auditClient) {
        this.repository = repository;
        this.auditClient = auditClient;
    }

    /**
     * Transition a verification case and enqueue its audit event in the SAME
     * transaction, so the status change and the audit record commit atomically.
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
        return verificationCase;
    }
}
