package com.o2o.carpooling.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal service-to-service API for capability gates (e.g. driver-service checking that a user
 * has passed real-name verification). Deliberately NOT under {@code /api/**}, so the Gateway does
 * not route it externally — only in-mesh Feign callers reach it.
 */
@RestController
@RequestMapping("/internal/identity")
class InternalIdentityController {

    private final IdentityVerificationService service;

    InternalIdentityController(IdentityVerificationService service) {
        this.service = service;
    }

    @GetMapping("/verifications/status")
    IdentityApprovalStatus status(@RequestParam String userId) {
        return new IdentityApprovalStatus(userId, service.isUserApproved(userId));
    }

    record IdentityApprovalStatus(String userId, boolean approved) {
    }
}
