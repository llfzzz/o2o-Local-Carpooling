package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.PaymentIntentStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operator-facing Demo Control for payments: trigger succeeded/failed/canceled/expired outcomes,
 * with delayed/duplicate/out-of-order delivery options. Demo-profile only (double-gated by {@link
 * DemoEndpoints#requireControl()}); the Gateway additionally requires OPERATOR/ADMIN for
 * /api/demo/control/**. Every outcome is delivered through the signed webhook ingestion pipeline —
 * this controller never writes intent state directly.
 */
@RestController
@RequestMapping("/api/demo/control/payment")
class DemoPaymentControlController {

    private final DemoPaymentControlService controlService;
    private final DemoEndpoints demoEndpoints;

    DemoPaymentControlController(DemoPaymentControlService controlService, DemoEndpoints demoEndpoints) {
        this.controlService = controlService;
        this.demoEndpoints = demoEndpoints;
    }

    /** Console listing: recent payment intents (optionally scoped to one order). Read-only. */
    @GetMapping("/intents")
    List<PaymentIntent> intents(
        @RequestParam(required = false) String orderId,
        @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        demoEndpoints.requireControl();
        return controlService.listIntents(orderId, limit);
    }

    @PostMapping("/{intentId}/callbacks")
    SimulationResponse simulate(@PathVariable String intentId, @RequestBody SimulationRequest request) {
        demoEndpoints.requireControl();
        SimulationMode mode = request.mode() == null ? SimulationMode.NORMAL : request.mode();
        List<DemoPaymentControlService.CallbackEmission> emissions =
            controlService.simulate(intentId, request.outcome(), mode, request.delaySeconds());
        return new SimulationResponse(intentId, controlService.currentStatus(intentId), emissions);
    }

    record SimulationRequest(PaymentIntentStatus outcome, SimulationMode mode, long delaySeconds) {
    }

    record SimulationResponse(
        String intentId,
        PaymentIntentStatus finalStatus,
        List<DemoPaymentControlService.CallbackEmission> emissions
    ) {
    }
}
