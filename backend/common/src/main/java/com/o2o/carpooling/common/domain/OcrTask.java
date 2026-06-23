package com.o2o.carpooling.common.domain;

import java.time.Instant;

public record OcrTask(
    String taskId,
    String fileObjectId,
    OcrResult result,
    Instant completedAt
) {
}
