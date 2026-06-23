package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.domain.PaymentSimulation;
import com.o2o.carpooling.common.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
class PaymentSimulationService {

    private final PaymentSimulationRepository paymentRepository;
    private final OrderClient orderClient;

    PaymentSimulationService(PaymentSimulationRepository paymentRepository, OrderClient orderClient) {
        this.paymentRepository = paymentRepository;
        this.orderClient = orderClient;
    }

    @Transactional
    PaymentSimulation simulateSuccess(SimulatePaymentCommand command) {
        validate(command);
        return paymentRepository.findByOrderIdAndIdempotencyKey(command.orderId(), command.idempotencyKey())
            .orElseGet(() -> createSimulation(command));
    }

    private PaymentSimulation createSimulation(SimulatePaymentCommand command) {
        OrderDetail order = orderClient.findOrder(command.orderId())
            .orElseThrow(() -> new IllegalArgumentException("order not found: " + command.orderId()));
        orderClient.markPaid(command.orderId());
        PaymentSimulation payment = new PaymentSimulation(
            "pay-" + UUID.randomUUID(),
            command.orderId(),
            order.amount(),
            PaymentStatus.SIMULATED_SUCCESS,
            Instant.now()
        );
        paymentRepository.save(payment, command.idempotencyKey());
        return payment;
    }

    private void validate(SimulatePaymentCommand command) {
        if (!StringUtils.hasText(command.orderId())) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (!StringUtils.hasText(command.idempotencyKey())) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
    }
}
