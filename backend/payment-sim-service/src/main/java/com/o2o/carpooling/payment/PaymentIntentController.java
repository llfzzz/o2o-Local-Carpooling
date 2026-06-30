package com.o2o.carpooling.payment;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
class PaymentIntentController {

    private final PaymentIntentService paymentIntentService;

    PaymentIntentController(PaymentIntentService paymentIntentService) {
        this.paymentIntentService = paymentIntentService;
    }

    @PostMapping("/intents")
    PaymentIntent create(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody CreateIntentRequest request
    ) {
        String idempotencyKey = StringUtils.hasText(request.idempotencyKey())
            ? request.idempotencyKey()
            : "intent-" + request.orderId();
        return paymentIntentService.createIntent(currentUserId, request.orderId(), idempotencyKey);
    }

    @GetMapping("/intents/{intentId}")
    PaymentIntent get(@PathVariable String intentId) {
        return paymentIntentService.get(intentId);
    }

    record CreateIntentRequest(String orderId, String idempotencyKey) {
    }
}
