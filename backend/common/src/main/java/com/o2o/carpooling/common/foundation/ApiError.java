package com.o2o.carpooling.common.foundation;

import java.time.Instant;
import java.util.Map;

public record ApiError(
    int status,
    String errorCode,
    String message,
    String traceId,
    Instant timestamp,
    Map<String, Object> details
) {

    public ApiError {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiError of(int status, String errorCode, String message, String traceId) {
        return of(status, errorCode, message, traceId, Map.of());
    }

    public static ApiError of(
        int status,
        String errorCode,
        String message,
        String traceId,
        Map<String, Object> details
    ) {
        return new ApiError(status, errorCode, message, traceId, Instant.now(), details);
    }
}
