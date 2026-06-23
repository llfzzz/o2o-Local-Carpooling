package com.o2o.carpooling.common.foundation;

import java.time.Instant;

public record JwtToken(
    SecurityPrincipal principal,
    String subject,
    String tokenId,
    Instant issuedAt,
    Instant expiresAt
) {
}
