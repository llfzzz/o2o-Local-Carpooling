package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.UserRole;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
    PaymentIntent get(
        @PathVariable String intentId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = "X-User-Roles", required = false) String currentRoles
    ) {
        return paymentIntentService.get(intentId, currentUserId, roles(currentRoles));
    }

    /** Parse the Gateway-injected, comma-separated roles header; spoofed inbound values are stripped upstream. */
    private Set<UserRole> roles(String header) {
        if (!StringUtils.hasText(header)) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(UserRole::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    record CreateIntentRequest(String orderId, String idempotencyKey) {
    }
}
