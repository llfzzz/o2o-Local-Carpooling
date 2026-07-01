package com.o2o.carpooling.common.domain;

import java.time.Instant;

/**
 * An asynchronous OCR task. {@code result} and {@code completedAt} are null until the task reaches
 * {@link OcrTaskStatus#COMPLETED}. {@code providerRef} is the OCR provider's own task handle.
 */
public record OcrTask(
    String taskId,
    String fileObjectId,
    OcrTaskStatus status,
    String providerRef,
    OcrResult result,
    Instant submittedAt,
    Instant completedAt
) {
}
