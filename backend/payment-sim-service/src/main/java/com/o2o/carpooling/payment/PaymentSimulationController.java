package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PaymentSimulation;
import com.o2o.carpooling.common.domain.PaymentStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
class PaymentSimulationController {

    @PostMapping("/simulate-success")
    PaymentSimulation simulateSuccess(@RequestBody SimulatePaymentRequest request) {
        return new PaymentSimulation(
            "pay-" + UUID.randomUUID(),
            request.orderId(),
            new Money(request.amount(), "CNY"),
            PaymentStatus.SIMULATED_SUCCESS,
            Instant.now()
        );
    }

    record SimulatePaymentRequest(String orderId, BigDecimal amount) {
    }
}
