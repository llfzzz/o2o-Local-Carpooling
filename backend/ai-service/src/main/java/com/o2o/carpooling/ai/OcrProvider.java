package com.o2o.carpooling.ai;

import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrTaskStatus;

/**
 * Provider seam for OCR. The demo provider recognizes documents locally via the mock policy; a real
 * OCR vendor (Aliyun/Tencent/…) implements the same async contract and is selected via
 * {@code providers.ocr.type} without changing the driver-verification flow. The task is submitted,
 * then polled to completion — matching how real OCR vendors work.
 */
interface OcrProvider {

    /** Provider key, matched against providers.ocr.type. */
    String name();

    OcrSubmission submit(OcrSubmitCommand command);

    OcrPollResult poll(String providerRef);

    record OcrSubmitCommand(String taskId, String fileObjectId) {
    }

    record OcrSubmission(String providerRef, OcrTaskStatus status) {
    }

    /** Poll result; {@code result} is non-null only once {@code status} is COMPLETED. */
    record OcrPollResult(OcrTaskStatus status, OcrResult result) {
    }
}
