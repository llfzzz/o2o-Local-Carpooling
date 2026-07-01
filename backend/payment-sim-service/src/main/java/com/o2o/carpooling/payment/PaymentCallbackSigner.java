package com.o2o.carpooling.payment;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Demo-only helper that produces correctly-signed callbacks so the Demo Control console can drive
 * the real ingestion pipeline instead of a back door. A real PSP signs on its own side with the
 * shared secret; here we sign with the same secret and feed the result straight into {@link
 * PaymentCallbackVerifier}, so signature/timestamp/nonce/idempotency are all genuinely exercised.
 */
class PaymentCallbackSigner {

    private final String webhookSecret;
    private final Clock clock;

    PaymentCallbackSigner(String webhookSecret, Clock clock) {
        this.webhookSecret = webhookSecret;
        this.clock = clock;
    }

    /** Sign {@code rawBody} with a fresh nonce and a timestamp back-dated by {@code backdate}. */
    SignedCallback sign(String provider, String rawBody, Duration backdate) {
        String timestamp = String.valueOf(clock.instant().minus(backdate).getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = PaymentCallbackSignature.hmacHex(webhookSecret, timestamp, nonce, rawBody);
        return new SignedCallback(provider, timestamp, nonce, signature, rawBody);
    }

    record SignedCallback(String provider, String timestamp, String nonce, String signature, String rawBody) {
    }
}
