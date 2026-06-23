package com.o2o.carpooling.common.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalApiExceptionHandlerTest {

    private final GlobalApiExceptionHandler handler = new GlobalApiExceptionHandler();

    @Test
    void mapsBusinessExceptionsToStructuredApiErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-test-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var response = handler.handleBusinessException(new BusinessException(
            HttpStatus.CONFLICT,
            "ORDER_STATE_CONFLICT",
            "order cannot be paid",
            Map.of("orderId", "order-1")
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().errorCode()).isEqualTo("ORDER_STATE_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("order cannot be paid");
        assertThat(response.getBody().traceId()).isEqualTo("trace-test-1");
        assertThat(response.getBody().details()).containsEntry("orderId", "order-1");

        RequestContextHolder.resetRequestAttributes();
    }
}
