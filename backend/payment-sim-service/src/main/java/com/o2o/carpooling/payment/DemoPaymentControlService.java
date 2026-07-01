package com.o2o.carpooling.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.carpooling.common.domain.PaymentIntentStatus;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demo Control orchestration for payments. Operators pick an outcome and a delivery mode; this
 * service builds correctly-signed callbacks and pushes them through the exact same {@link
 * PaymentCallbackVerifier} + {@link PaymentCallbackService} path a real PSP webhook would take —
 * there is deliberately no shortcut that mutates intent state directly.
 *
 * <p>Each emission is reported back (accepted + resulting status, or the rejection code) so the
 * pipeline's protections — replay/timestamp rejection, event-id idempotency, terminal-state
 * safety — are observable in the console.
 */
@Service
class DemoPaymentControlService {

    private static final String PROVIDER = "demo";

    private final PaymentIntentRepository repository;
    private final PaymentCallbackVerifier verifier;
    private final PaymentCallbackService callbackService;
    private final PaymentCallbackSigner signer;
    private final ObjectMapper objectMapper;

    DemoPaymentControlService(
        PaymentIntentRepository repository,
        PaymentCallbackVerifier verifier,
        PaymentCallbackService callbackService,
        PaymentCallbackSigner signer,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.verifier = verifier;
        this.callbackService = callbackService;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    List<CallbackEmission> simulate(String intentId, PaymentIntentStatus outcome, SimulationMode mode, long delaySeconds) {
        requireExists(intentId);
        if (outcome == null || !outcome.isTerminal()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAYMENT_CALLBACK_OUTCOME_INVALID",
                "outcome must be one of SUCCEEDED, FAILED, CANCELED, EXPIRED");
        }
        Duration backdate = Duration.ofSeconds(Math.max(0, delaySeconds));
        List<CallbackEmission> emissions = new ArrayList<>();

        String primaryEventId = "evt-" + UUID.randomUUID();
        emissions.add(emit(primaryEventId, intentId, outcome, backdate));

        if (mode == SimulationMode.DUPLICATE) {
            // Same event id, delivered again — must be an idempotent no-op.
            emissions.add(emit(primaryEventId, intentId, outcome, backdate));
        } else if (mode == SimulationMode.OUT_OF_ORDER) {
            // A conflicting terminal outcome after the first — must be ignored (terminal wins).
            PaymentIntentStatus conflicting = outcome == PaymentIntentStatus.SUCCEEDED
                ? PaymentIntentStatus.FAILED
                : PaymentIntentStatus.SUCCEEDED;
            emissions.add(emit("evt-" + UUID.randomUUID(), intentId, conflicting, backdate));
        }
        return emissions;
    }

    PaymentIntentStatus currentStatus(String intentId) {
        return requireExists(intentId).status();
    }

    private CallbackEmission emit(String eventId, String intentId, PaymentIntentStatus outcome, Duration backdate) {
        String body = body(eventId, intentId, outcome);
        PaymentCallbackSigner.SignedCallback signed = signer.sign(PROVIDER, body, backdate);
        try {
            verifier.verify(signed.provider(), signed.timestamp(), signed.nonce(), signed.signature(), signed.rawBody());
            PaymentIntent intent = callbackService.apply(signed.rawBody());
            return new CallbackEmission(eventId, outcome, true, intent.status(), null);
        } catch (BusinessException rejected) {
            return new CallbackEmission(eventId, outcome, false, null, rejected.errorCode());
        }
    }

    private PaymentIntent requireExists(String intentId) {
        return repository.findByIntentId(intentId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PAYMENT_INTENT_NOT_FOUND",
                "payment intent not found: " + intentId));
    }

    private String body(String eventId, String intentId, PaymentIntentStatus outcome) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("intentId", intentId);
        payload.put("outcome", outcome.name());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize demo payment callback", exception);
        }
    }

    /** One delivered (or rejected) callback, as observed by the operator. */
    record CallbackEmission(
        String eventId,
        PaymentIntentStatus outcome,
        boolean accepted,
        PaymentIntentStatus resultStatus,
        String rejectionCode
    ) {
    }
}
