package com.o2o.carpooling.common.domain;

import java.time.Instant;
import java.util.Map;

public record VerificationCase(
    String caseId,
    String userId,
    DriverVerificationStatus status,
    Map<String, String> uploadedFileIds,
    OcrResult ocrResult,
    Instant submittedAt
) {
    public VerificationCase {
        uploadedFileIds = Map.copyOf(uploadedFileIds);
    }
}
