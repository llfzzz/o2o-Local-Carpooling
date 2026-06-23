package com.o2o.carpooling.common.event;

import java.time.Instant;

public record OcrCompleted(String taskId, String fileObjectId, double confidence, Instant occurredAt) {
}
