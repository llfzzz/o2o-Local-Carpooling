package com.o2o.carpooling.common.foundation;

import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> details;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, Map.of());
    }

    public BusinessException(
        HttpStatus status,
        String errorCode,
        String message,
        Map<String, Object> details
    ) {
        super(message);
        this.status = Objects.requireNonNull(status, "status is required");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode is required");
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
