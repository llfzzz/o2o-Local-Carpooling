package com.o2o.carpooling.driver;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal service-to-service API answering "may this user act as a driver?".
 *
 * <p>Deliberately NOT under {@code /api/**}, so the Gateway does not route it externally — only
 * in-mesh Feign callers reach it. Mirrors {@code InternalIdentityController} in identity-service.
 *
 * <p>Driver capability is the conjunction of two independent gates, both server-side:
 * real-name identity APPROVED, and at least one APPROVED document review case. A client claiming
 * the {@code DRIVER} role proves neither.
 */
@RestController
@RequestMapping("/internal/drivers")
class InternalDriverController {

    private final DriverVerificationRepository repository;
    private final IdentityClient identityClient;

    InternalDriverController(DriverVerificationRepository repository, IdentityClient identityClient) {
        this.repository = repository;
        this.identityClient = identityClient;
    }

    @GetMapping("/{userId}/capability")
    DriverCapability capability(@PathVariable String userId) {
        boolean identityApproved = identityClient.status(userId).approved();
        boolean documentsApproved = repository.hasApprovedCase(userId);
        return new DriverCapability(userId, identityApproved && documentsApproved, identityApproved, documentsApproved);
    }

    record DriverCapability(String userId, boolean approved, boolean identityApproved, boolean documentsApproved) {
    }
}
