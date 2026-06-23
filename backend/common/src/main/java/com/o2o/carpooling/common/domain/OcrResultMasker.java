package com.o2o.carpooling.common.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OcrResultMasker {

    private OcrResultMasker() {
    }

    public static OcrResult maskSensitiveFields(OcrResult result) {
        Map<String, String> maskedFields = new LinkedHashMap<>();
        result.fields().forEach((key, value) -> maskedFields.put(key, isSensitiveKey(key) ? mask(value) : value));
        return new OcrResult(result.provider(), result.confidence(), maskedFields);
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("licenseno")
            || normalized.contains("idno")
            || normalized.contains("cardno")
            || normalized.contains("plateno")
            || normalized.contains("certificate");
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "****";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
