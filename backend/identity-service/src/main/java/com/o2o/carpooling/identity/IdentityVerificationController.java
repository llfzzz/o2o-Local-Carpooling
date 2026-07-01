package com.o2o.carpooling.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rider/driver-facing identity verification: start a session and poll its status. The outcome is
 * never returned inline here — it is decided asynchronously (operator/provider-driven) and
 * delivered to the Demo inbox; this endpoint only reflects the server-authoritative session state.
 */
@RestController
@RequestMapping("/api/identity")
class IdentityVerificationController {

    private final IdentityVerificationService service;

    IdentityVerificationController(IdentityVerificationService service) {
        this.service = service;
    }

    @PostMapping("/verifications")
    IdentityVerification start(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody StartRequest request
    ) {
        return service.start(currentUserId, request.realName(), request.idNumber(), request.idempotencyKey());
    }

    @GetMapping("/verifications/{verificationId}")
    IdentityVerification get(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String verificationId
    ) {
        return service.get(verificationId, currentUserId);
    }

    record StartRequest(String realName, String idNumber, String idempotencyKey) {
    }
}
