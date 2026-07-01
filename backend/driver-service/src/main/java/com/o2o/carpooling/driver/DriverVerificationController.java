package com.o2o.carpooling.driver;

import com.o2o.carpooling.common.domain.DriverVerificationStatus;
import com.o2o.carpooling.common.domain.MockOcrPolicy;
import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.VerificationCase;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
class DriverVerificationController {

    private final MockOcrPolicy ocrPolicy = new MockOcrPolicy();
    private final DriverVerificationRepository verificationRepository;
    private final DriverVerificationService verificationService;
    private final IdentityClient identityClient;

    DriverVerificationController(
        DriverVerificationRepository verificationRepository,
        DriverVerificationService verificationService,
        IdentityClient identityClient
    ) {
        this.verificationRepository = verificationRepository;
        this.verificationService = verificationService;
        this.identityClient = identityClient;
    }

    @PostMapping("/verification-cases")
    VerificationCase submit(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody VerificationSubmitRequest request
    ) {
        String userId = StringUtils.hasText(currentUserId) ? currentUserId : request.userId();
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_ID_REQUIRED", "userId is required");
        }
        // S17 gate: driver capability requires the user's real-name identity to be APPROVED,
        // on top of the operator's manual review of the documents below.
        if (!identityClient.status(userId).approved()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DRIVER_IDENTITY_NOT_VERIFIED",
                "real-name identity verification must be APPROVED before submitting driver documents");
        }
        OcrResult result = ocrPolicy.inspect(request.drivingLicenseFileId());
        VerificationCase verificationCase = new VerificationCase(
            "verify-" + UUID.randomUUID(),
            userId,
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
        return verificationService.transition(caseId, DriverVerificationStatus.APPROVED, "DRIVER_VERIFICATION_APPROVED", currentUserId);
    }

    @PostMapping("/verification-cases/{caseId}/reject")
    VerificationCase reject(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String caseId
    ) {
        return verificationService.transition(caseId, DriverVerificationStatus.REJECTED, "DRIVER_VERIFICATION_REJECTED", currentUserId);
    }

    record VerificationSubmitRequest(String userId, String drivingLicenseFileId, String vehicleLicenseFileId) {
    }
}
