package com.o2o.carpooling.common.domain;

import java.util.Map;

public final class MockOcrPolicy {

    public OcrResult inspect(String fileObjectId) {
        if (fileObjectId == null || fileObjectId.isBlank()) {
            throw new IllegalArgumentException("fileObjectId is required");
        }
        return new OcrResult(
            "mock-ocr",
            0.91,
            Map.of(
                "name", "张三",
                "licenseNo", "MOCK-" + Math.abs(fileObjectId.hashCode()),
                "expiresAt", "2032-12-31",
                "documentType", "driving-license"
            )
        );
    }
}
