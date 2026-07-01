package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Verifies inbound payment provider webhooks the way a real PSP integration must: an HMAC-SHA256
 * signature over {@code timestamp.nonce.rawBody}, a freshness window on the signed timestamp, and
 * single-use nonces for replay protection. Fail-closed if no webhook secret is configured.
 *
 * <p>The signature is checked <em>before</em> the nonce is registered, so unauthenticated requests
 * can never poison the replay store. All rejections use generic messages that never echo the
 * secret or the computed signature.
 */
class PaymentCallbackVerifier {

    private final String webhookSecret;
    private final SeenNonceStore nonceStore;
    private final Duration timestampTolerance;
    private final Clock clock;

    PaymentCallbackVerifier(String webhookSecret, SeenNonceStore nonceStore, Duration timestampTolerance, Clock clock) {
        this.webhookSecret = webhookSecret;
        this.nonceStore = nonceStore;
        this.timestampTolerance = timestampTolerance;
        this.clock = clock;
    }

    /** Verify a callback, throwing {@link BusinessException} on any failure. */
    void verify(String provider, String timestamp, String nonce, String signature, String rawBody) {
        if (!StringUtils.hasText(webhookSecret)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_WEBHOOK_UNCONFIGURED",
                "payment webhook secret is not configured");
        }
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            throw signatureInvalid();
        }
        String expected = PaymentCallbackSignature.hmacHex(webhookSecret, timestamp, nonce, rawBody);
        if (!constantTimeEquals(expected, signature.trim().toLowerCase(Locale.ROOT))) {
            throw signatureInvalid();
        }
        long callbackEpochSecond = parseTimestamp(timestamp);
        long skewSeconds = Math.abs(clock.instant().getEpochSecond() - callbackEpochSecond);
        if (skewSeconds > timestampTolerance.toSeconds()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "PAYMENT_CALLBACK_TIMESTAMP",
                "payment callback timestamp is outside the accepted window");
        }
        // Keep a nonce reserved for at least the full acceptance window so it cannot be replayed.
        if (!nonceStore.registerIfAbsent(provider + ":" + nonce, timestampTolerance.multipliedBy(2))) {
            throw new BusinessException(HttpStatus.CONFLICT, "PAYMENT_CALLBACK_REPLAY",
                "payment callback nonce has already been used");
        }
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp.trim());
        } catch (NumberFormatException notNumeric) {
            throw signatureInvalid();
        }
    }

    private BusinessException signatureInvalid() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "PAYMENT_CALLBACK_SIGNATURE_INVALID",
            "payment callback signature is invalid");
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
    }
}
