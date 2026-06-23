package com.o2o.carpooling.common.foundation;

import com.o2o.carpooling.common.domain.UserRole;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public record SecurityPrincipal(String userId, Set<UserRole> roles) {

    public SecurityPrincipal {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles are required"));
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("at least one role is required");
        }
    }

    public boolean hasAnyRole(UserRole... requiredRoles) {
        return Arrays.stream(requiredRoles).anyMatch(roles::contains);
    }

    public String rolesHeaderValue() {
        return roles.stream().map(Enum::name).sorted().reduce((left, right) -> left + "," + right).orElse("");
    }
}
