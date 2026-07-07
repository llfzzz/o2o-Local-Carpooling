package com.o2o.carpooling.identity;

import com.o2o.carpooling.common.domain.IdentityVerificationStatus;
import com.o2o.carpooling.common.domain.LivenessCheckStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operator-facing Demo Control for identity verification: drive the liveness sub-check and the
 * overall session to any outcome (PASS/FAIL/TIMEOUT/RETRY and APPROVED/REJECTED/TIMEOUT/RETRY).
 * Demo-profile only (double-gated by {@link DemoEndpoints#requireControl()}); the Gateway
 * additionally requires OPERATOR/ADMIN for /api/demo/control/**. Outcomes go through the same
 * authoritative state machines + inbox delivery as a real provider callback would.
 */
@RestController
@RequestMapping("/api/demo/control/identity")
class IdentityDemoControlController {

    private final IdentityVerificationService service;
    private final DemoEndpoints demoEndpoints;

    IdentityDemoControlController(IdentityVerificationService service, DemoEndpoints demoEndpoints) {
        this.service = service;
        this.demoEndpoints = demoEndpoints;
    }

    /** Console listing: recent verification sessions so the operator can pick a target. Read-only. */
    @GetMapping("/verifications")
    List<IdentityVerification> verifications(@RequestParam(required = false, defaultValue = "20") int limit) {
        demoEndpoints.requireControl();
        return service.listRecent(limit);
    }

    @PostMapping("/{verificationId}/liveness")
    IdentityVerification liveness(@PathVariable String verificationId, @RequestBody LivenessRequest request) {
        demoEndpoints.requireControl();
        return service.applyLivenessOutcome(verificationId, request.outcome());
    }

    @PostMapping("/{verificationId}/session")
    IdentityVerification session(@PathVariable String verificationId, @RequestBody SessionRequest request) {
        demoEndpoints.requireControl();
        return service.applySessionOutcome(verificationId, request.outcome());
    }

    record LivenessRequest(LivenessCheckStatus outcome) {
    }

    record SessionRequest(IdentityVerificationStatus outcome) {
    }
}
