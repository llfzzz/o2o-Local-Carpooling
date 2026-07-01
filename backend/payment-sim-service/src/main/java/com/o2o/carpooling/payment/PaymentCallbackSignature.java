package com.o2o.carpooling.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * The one canonical payment-callback signing scheme, shared by the inbound {@link
 * PaymentCallbackVerifier} and the demo-only {@link PaymentCallbackSigner} so the two can never
 * drift apart. Signature = hex(HMAC-SHA256(secret, "{timestamp}.{nonce}.{rawBody}")).
 */
final class PaymentCallbackSignature {

    private PaymentCallbackSignature() {
    }

    static String canonicalPayload(String timestamp, String nonce, String rawBody) {
        return timestamp + "." + nonce + "." + (rawBody == null ? "" : rawBody);
    }

    static String hmacHex(String secret, String timestamp, String nonce, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(canonicalPayload(timestamp, nonce, rawBody).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to compute payment callback HMAC", exception);
        }
    }
}
