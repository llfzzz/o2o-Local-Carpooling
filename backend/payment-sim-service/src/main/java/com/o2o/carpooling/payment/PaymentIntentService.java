package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class PaymentIntentService {

    private final PaymentIntentRepository repository;
    private final OrderClient orderClient;
    private final List<PaymentProvider> providers;
    private final ProviderProperties providerProperties;
    private final Clock clock;

    PaymentIntentService(
        PaymentIntentRepository repository,
        OrderClient orderClient,
        List<PaymentProvider> providers,
        ProviderProperties providerProperties,
        Clock clock
    ) {
        this.repository = repository;
        this.orderClient = orderClient;
        this.providers = providers;
        this.providerProperties = providerProperties;
        this.clock = clock;
    }

    @Transactional
    PaymentIntent createIntent(String currentUserId, String orderId, String idempotencyKey) {
        if (!StringUtils.hasText(orderId)) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        return repository.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey)
            .orElseGet(() -> create(currentUserId, orderId, idempotencyKey));
    }

    PaymentIntent get(String intentId) {
        return repository.findByIntentId(intentId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PAYMENT_INTENT_NOT_FOUND",
                "payment intent not found: " + intentId));
    }

    private PaymentIntent create(String currentUserId, String orderId, String idempotencyKey) {
        OrderDetail order = orderClient.findOrder(orderId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "order not found: " + orderId));
        // Authorization: only the order owner may pay (when the Gateway provides the principal).
        if (StringUtils.hasText(currentUserId) && !currentUserId.equals(order.riderId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PAYMENT_FORBIDDEN", "only the order owner can pay this order");
        }
        PaymentProvider provider = provider();
        String intentId = "pi-" + UUID.randomUUID();
        PaymentProvider.PaymentProviderIntent init = provider.createIntent(
            new PaymentProvider.CreateIntentCommand(intentId, orderId, order.amount(), idempotencyKey));
        Instant now = clock.instant();
        PaymentIntent intent = new PaymentIntent(
            intentId, orderId, order.riderId(), order.amount(), init.status(), provider.name(), init.providerRef(), now, now);
        repository.save(intent, idempotencyKey);
        return intent;
    }

    private PaymentProvider provider() {
        String type = providerProperties.getPayment().getType();
        return providers.stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(type))
            .findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNCONFIGURED",
                "no payment provider configured for type '" + type + "'"));
    }
}
