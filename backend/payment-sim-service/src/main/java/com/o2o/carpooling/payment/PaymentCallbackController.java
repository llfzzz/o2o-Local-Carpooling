package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.PaymentIntentStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingress for provider payment webhooks: {@code POST /api/payments/callbacks/{provider}}. This is
 * the ONLY path that moves a payment intent to a terminal state — there is no front-end back door.
 * The Gateway routes this path without a JWT (a PSP has none); authenticity comes entirely from the
 * HMAC signature + timestamp + nonce headers, verified by {@link PaymentCallbackVerifier}.
 *
 * <p>The body is read as a raw string so the signature is verified over the exact bytes the caller
 * signed, before any JSON parsing.
 */
@RestController
@RequestMapping("/api/payments")
class PaymentCallbackController {

    private final PaymentCallbackVerifier verifier;
    private final PaymentCallbackService callbackService;

    PaymentCallbackController(PaymentCallbackVerifier verifier, PaymentCallbackService callbackService) {
        this.verifier = verifier;
        this.callbackService = callbackService;
    }

    @PostMapping("/callbacks/{provider}")
    CallbackAck receive(
        @PathVariable String provider,
        @RequestHeader(name = "X-Payment-Timestamp", required = false) String timestamp,
        @RequestHeader(name = "X-Payment-Nonce", required = false) String nonce,
        @RequestHeader(name = "X-Payment-Signature", required = false) String signature,
        @RequestBody(required = false) String rawBody
    ) {
        verifier.verify(provider, timestamp, nonce, signature, rawBody);
        PaymentIntent intent = callbackService.apply(rawBody);
        return new CallbackAck(intent.intentId(), intent.status());
    }

    record CallbackAck(String intentId, PaymentIntentStatus status) {
    }
}
