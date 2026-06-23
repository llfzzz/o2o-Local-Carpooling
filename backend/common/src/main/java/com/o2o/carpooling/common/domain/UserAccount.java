package com.o2o.carpooling.common.domain;

import java.time.Instant;
import java.util.Set;

public record UserAccount(
    String userId,
    String phone,
    Set<UserRole> roles,
    Instant createdAt
) {
    public UserAccount {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        roles = Set.copyOf(roles);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("at least one role is required");
        }
    }
}
