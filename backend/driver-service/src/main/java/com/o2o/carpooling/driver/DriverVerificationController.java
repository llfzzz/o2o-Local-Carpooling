package com.o2o.carpooling.driver;

import com.o2o.carpooling.common.domain.DriverVerificationStatus;
import com.o2o.carpooling.common.domain.MockOcrPolicy;
import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.VerificationCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
class DriverVerificationController {

    private final MockOcrPolicy ocrPolicy = new MockOcrPolicy();
    private final DriverVerificationRepository verificationRepository;
    private final AuditClient auditClient;

    DriverVerificationController(DriverVerificationRepository verificationRepository, AuditClient auditClient) {
        this.verificationRepository = verificationRepository;
        this.auditClient = auditClient;
    }

    @PostMapping("/verification-cases")
    VerificationCase submit(@RequestBody VerificationSubmitRequest request) {
        OcrResult result = ocrPolicy.inspect(request.drivingLicenseFileId());
        VerificationCase verificationCase = new VerificationCase(
            "verify-" + UUID.randomUUID(),
            request.userId(),
            DriverVerificationStatus.OCR_REVIEWABLE,
            Map.of(
                "drivingLicense", request.drivingLicenseFileId(),
                "vehicleLicense", request.vehicleLicenseFileId()
            ),
            result,
            Instant.now()
        );
        verificationRepository.save(verificationCase);
        return verificationRepository.findByCaseId(verificationCase.caseId()).orElseThrow();
    }

    @GetMapping("/verification-cases")
    List<VerificationCase> listCases() {
        return verificationRepository.listCases();
    }

    @PostMapping("/verification-cases/{caseId}/approve")
    VerificationCase approve(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String caseId
    ) {
        return transition(caseId, DriverVerificationStatus.APPROVED, "DRIVER_VERIFICATION_APPROVED", currentUserId);
    }

    @PostMapping("/verification-cases/{caseId}/reject")
    VerificationCase reject(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String caseId
    ) {
        return transition(caseId, DriverVerificationStatus.REJECTED, "DRIVER_VERIFICATION_REJECTED", currentUserId);
    }

    private VerificationCase transition(String caseId, DriverVerificationStatus status, String action, String actorId) {
        verificationRepository.updateStatus(caseId, status);
        VerificationCase verificationCase = verificationRepository.findByCaseId(caseId)
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

    record VerificationSubmitRequest(String userId, String drivingLicenseFileId, String vehicleLicenseFileId) {
    }
}
