package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.PaymentIntentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Interactive mock PSP. Intents start in REQUIRES_PAYMENT; the actual outcome is driven later by
 * an operator through the Demo Control endpoint, which emits a signed callback into the webhook
 * ingress — exactly how a real PSP would notify us. Active when providers.payment.type=demo.
 */
@Component
@ConditionalOnProperty(prefix = "providers.payment", name = "type", havingValue = "demo")
class DemoPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public PaymentProviderIntent createIntent(CreateIntentCommand command) {
        return new PaymentProviderIntent(PaymentIntentStatus.REQUIRES_PAYMENT, "demo-ref-" + command.intentId());
    }
}
