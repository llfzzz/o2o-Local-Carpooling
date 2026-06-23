package com.o2o.carpooling.common.foundation;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @Test
    void propagatesIncomingTraceIdAndClearsMdc() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
            assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY)).isEqualTo("trace-123")
        );

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY)).isNull();
    }
}
