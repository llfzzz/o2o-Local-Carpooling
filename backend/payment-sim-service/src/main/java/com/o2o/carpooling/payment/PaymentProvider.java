package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PaymentIntentStatus;

/**
 * Provider seam for payments. The demo provider creates intents locally and emits signed
 * callbacks on demand; a real PSP (Stripe/Alipay/WeChat Pay) implements the same contract and is
 * selected via {@code providers.payment.type} without changing the order flow. Inbound callbacks
 * are verified through {@link PaymentCallbackVerifier}.
 */
interface PaymentProvider {

    /** Provider key, matched against providers.payment.type. */
    String name();

    PaymentProviderIntent createIntent(CreateIntentCommand command);

    record CreateIntentCommand(String intentId, String orderId, Money amount, String idempotencyKey) {
    }

    record PaymentProviderIntent(PaymentIntentStatus status, String providerRef) {
    }
}
