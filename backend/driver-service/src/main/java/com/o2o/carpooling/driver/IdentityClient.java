package com.o2o.carpooling.driver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Checks a user's real-name verification status via identity-service's internal API (not routed
 * through the Gateway). Used to gate driver-capability actions on identity being APPROVED.
 */
@FeignClient(name = "identity-service", contextId = "driverIdentityClient")
interface IdentityClient {

    @GetMapping("/internal/identity/verifications/status")
    IdentityApprovalStatus status(@RequestParam("userId") String userId);

    record IdentityApprovalStatus(String userId, boolean approved) {
    }
}
