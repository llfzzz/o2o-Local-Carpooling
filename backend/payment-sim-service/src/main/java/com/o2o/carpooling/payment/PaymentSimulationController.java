package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.PaymentSimulation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
class PaymentSimulationController {

    private final PaymentSimulationService paymentSimulationService;

    PaymentSimulationController(PaymentSimulationService paymentSimulationService) {
        this.paymentSimulationService = paymentSimulationService;
    }

    @PostMapping("/simulations")
    PaymentSimulation simulate(@RequestBody SimulatePaymentRequest request) {
        return paymentSimulationService.simulateSuccess(new SimulatePaymentCommand(
            request.orderId(),
            resolveIdempotencyKey(request)
        ));
    }

    @PostMapping("/simulate-success")
    PaymentSimulation simulateSuccess(@RequestBody SimulatePaymentRequest request) {
        return simulate(request);
    }

    private String resolveIdempotencyKey(SimulatePaymentRequest request) {
        return StringUtils.hasText(request.idempotencyKey()) ? request.idempotencyKey() : "compat-" + request.orderId();
    }

    record SimulatePaymentRequest(String orderId, BigDecimal amount, String idempotencyKey) {
    }
}
