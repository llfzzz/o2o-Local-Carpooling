package com.o2o.carpooling.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.carpooling.common.domain.PaymentIntentStatus;
import com.o2o.carpooling.common.domain.PaymentIntentStateMachine;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;

/**
 * Applies a verified payment provider callback to the payment intent. The signature/replay checks
 * happen upstream in {@link PaymentCallbackVerifier}; this service is the authoritative writer of
 * the intent state and is safe under duplicate and out-of-order delivery:
 *
 * <ul>
 *   <li>Every distinct {@code eventId} is recorded once; a repeat is an idempotent no-op.</li>
 *   <li>Transitions go through {@link PaymentIntentStateMachine}; a terminal intent is never
 *       resurrected, so a late/conflicting outcome after a terminal one is ignored.</li>
 *   <li>Only a real transition into {@code SUCCEEDED} notifies order-service (itself idempotent).</li>
 * </ul>
 */
@Service
class PaymentCallbackService {

    private final PaymentIntentRepository repository;
    private final OrderClient orderClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PaymentIntentStateMachine stateMachine = new PaymentIntentStateMachine();

    PaymentCallbackService(PaymentIntentRepository repository, OrderClient orderClient, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.orderClient = orderClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    PaymentIntent apply(String rawBody) {
        CallbackPayload payload = parse(rawBody);
        PaymentIntent intent = repository.findByIntentId(payload.intentId())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PAYMENT_INTENT_NOT_FOUND",
                "payment intent not found: " + payload.intentId()));
        PaymentIntentStatus target = terminalStatus(payload.outcome());
        Instant now = clock.instant();

        // Idempotency by event id: a repeated event is a no-op, regardless of current state.
        if (!repository.recordCallbackEvent(payload.eventId(), intent.intentId(), target.name(), now)) {
            return currentOr(intent);
        }
        // A terminal intent has already reached its final outcome; never overwrite it.
        if (intent.status().isTerminal() || !stateMachine.canTransition(intent.status(), target)) {
            return currentOr(intent);
        }
        boolean moved = repository.transition(intent.intentId(), intent.status(), target, now);
        if (moved && target == PaymentIntentStatus.SUCCEEDED) {
            orderClient.markPaid(intent.orderId());
        }
        return currentOr(intent);
    }

    private PaymentIntent currentOr(PaymentIntent fallback) {
        return repository.findByIntentId(fallback.intentId()).orElse(fallback);
    }

    private PaymentIntentStatus terminalStatus(String outcome) {
        PaymentIntentStatus status;
        try {
            status = PaymentIntentStatus.valueOf(outcome.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw malformed("unknown outcome");
        }
        if (!status.isTerminal()) {
            throw malformed("outcome is not terminal");
        }
        return status;
    }

    private CallbackPayload parse(String rawBody) {
        CallbackPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, CallbackPayload.class);
        } catch (Exception invalid) {
            throw malformed("body is not valid JSON");
        }
        if (payload == null
            || !StringUtils.hasText(payload.eventId())
            || !StringUtils.hasText(payload.intentId())
            || !StringUtils.hasText(payload.outcome())) {
            throw malformed("eventId, intentId and outcome are required");
        }
        return payload;
    }

    private BusinessException malformed(String detail) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "PAYMENT_CALLBACK_MALFORMED",
            "payment callback is malformed: " + detail);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CallbackPayload(String eventId, String intentId, String outcome) {
    }
}
