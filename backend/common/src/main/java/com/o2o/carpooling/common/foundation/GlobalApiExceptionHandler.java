package com.o2o.carpooling.common.foundation;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException exception) {
        return buildResponse(
            exception.status(),
            exception.errorCode(),
            exception.getMessage(),
            exception.details()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "request validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolationException(ConstraintViolationException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
            details.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "request validation failed", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException exception) {
        return buildResponse(HttpStatus.CONFLICT, "ILLEGAL_STATE", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "request body is malformed or unreadable", Map.of());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiError> handleThrowable(Throwable exception) {
        // Framework exceptions carry their own HTTP semantics (unmapped path -> 404, wrong
        // method -> 405, missing param -> 400, ...). Masking them as 500 INTERNAL_ERROR makes
        // a client calling an endpoint that does not exist on this deployment look like a
        // server crash, so surface the real status with a stable error code instead.
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null && !status.is5xxServerError()) {
                log.warn("Request rejected with framework status {}: {}", status.value(), exception.getMessage());
                return buildResponse(status, frameworkErrorCode(status), frameworkErrorMessage(status), Map.of());
            }
        }
        log.error("Unhandled request failure", exception);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "internal server error",
            Map.of()
        );
    }

    private String frameworkErrorCode(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case BAD_REQUEST -> "BAD_REQUEST";
            default -> status.name();
        };
    }

    private String frameworkErrorMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "resource not found";
            case METHOD_NOT_ALLOWED -> "method not allowed";
            default -> status.getReasonPhrase().toLowerCase();
        };
    }

    private ResponseEntity<ApiError> buildResponse(
        HttpStatus status,
        String errorCode,
        String message,
        Map<String, Object> details
    ) {
        return ResponseEntity.status(status).body(
            ApiError.of(status.value(), errorCode, message, currentTraceId(), details)
        );
    }

    private String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader(TraceIdFilter.TRACE_ID_HEADER);
        }
        return null;
    }
}
