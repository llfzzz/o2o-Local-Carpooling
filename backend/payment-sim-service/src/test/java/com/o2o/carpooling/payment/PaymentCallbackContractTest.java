package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PaymentIntentStatus;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.GlobalApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP wire-contract test for the payment webhook endpoint: pins the header names, path, raw body
 * pass-through, and the success/rejection status codes so the PSP-facing contract cannot drift.
 */
@WebMvcTest(PaymentCallbackController.class)
@Import(GlobalApiExceptionHandler.class)
class PaymentCallbackContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentCallbackVerifier verifier;

    @MockBean
    private PaymentCallbackService callbackService;

    private static final String BODY = "{\"eventId\":\"evt-1\",\"intentId\":\"pi-1\",\"outcome\":\"SUCCEEDED\"}";

    @Test
    void acceptsSignedCallbackAndReturnsAck() throws Exception {
        when(callbackService.apply(BODY)).thenReturn(new PaymentIntent(
            "pi-1", "order-1", "user-1", new Money(new java.math.BigDecimal("18.00"), "CNY"),
            PaymentIntentStatus.SUCCEEDED, "demo", "demo-ref", Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/payments/callbacks/{provider}", "demo")
                .header("X-Payment-Timestamp", "1782900000")
                .header("X-Payment-Nonce", "nonce-1")
                .header("X-Payment-Signature", "deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intentId").value("pi-1"))
            .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // The controller must verify with the exact header values + raw body before applying.
        verify(verifier).verify(eq("demo"), eq("1782900000"), eq("nonce-1"), eq("deadbeef"), eq(BODY));
        verify(callbackService).apply(BODY);
    }

    @Test
    void rejectsBadSignatureWith401() throws Exception {
        doThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "PAYMENT_CALLBACK_SIGNATURE_INVALID", "bad"))
            .when(verifier).verify(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/payments/callbacks/{provider}", "demo")
                .header("X-Payment-Timestamp", "1782900000")
                .header("X-Payment-Nonce", "nonce-1")
                .header("X-Payment-Signature", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("PAYMENT_CALLBACK_SIGNATURE_INVALID"));
    }

    @Test
    void answersUnmappedPathWith404NotFoundInsteadOf500() throws Exception {
        // Regression guard for version-skewed deployments: a path that does not exist on this
        // service (e.g. a console calling a newer endpoint) must read as 404 NOT_FOUND, not as
        // INTERNAL_ERROR — a 500 here sent the live-site diagnosis chasing phantom crashes.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/payments/route-that-does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
