package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.BusinessException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Resolves the authoritative user for a login. New phones get the baseline RIDER role only;
 * elevated roles (DRIVER, OPERATOR, ADMIN) are granted server-side through their own flows
 * (driver onboarding, operator provisioning) — never selectable by the client.
 */
@Component
class UserAccounts {

    private final UserFeignClient userFeignClient;

    UserAccounts(UserFeignClient userFeignClient) {
        this.userFeignClient = userFeignClient;
    }

    UserAccount getOrCreate(String userId, String phone) {
        try {
            return userFeignClient.get(userId);
        } catch (FeignException.NotFound notFound) {
            return userFeignClient.upsert(new UserFeignClient.UpsertRequest(userId, phone, Set.of(UserRole.RIDER)));
        }
    }

    /**
     * Demo-only: provision an operator (OPERATOR + ADMIN) so the admin console and the Demo Control
     * endpoints can be exercised. Gated upstream by DemoEndpoints.requireSeed(); impossible outside
     * the demo profile.
     */
    UserAccount seedOperator(String userId, String phone) {
        return userFeignClient.upsert(new UserFeignClient.UpsertRequest(userId, phone, Set.of(UserRole.OPERATOR, UserRole.ADMIN)));
    }

    /** Fetch an existing user (e.g. on refresh); never creates. Roles are read fresh each time. */
    UserAccount require(String userId) {
        try {
            return userFeignClient.get(userId);
        } catch (FeignException.NotFound notFound) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "user no longer exists");
        }
    }
}
