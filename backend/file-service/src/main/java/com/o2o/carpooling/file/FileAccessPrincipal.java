package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.UserRole;

import java.util.Set;

record FileAccessPrincipal(String userId, Set<UserRole> roles) {
    FileAccessPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    boolean hasOperatorAccess() {
        return roles.contains(UserRole.OPERATOR) || roles.contains(UserRole.ADMIN);
    }
}
