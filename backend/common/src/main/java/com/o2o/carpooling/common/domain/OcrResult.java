package com.o2o.carpooling.common.domain;

import java.util.Map;
import java.util.Objects;

public record OcrResult(String provider, double confidence, Map<String, String> fields) {
    public OcrResult {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        fields = Map.copyOf(Objects.requireNonNull(fields, "fields is required"));
    }
}
