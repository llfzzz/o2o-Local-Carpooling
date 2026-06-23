package com.o2o.carpooling.common.foundation;

import com.o2o.carpooling.common.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    private static final Instant NOW = Instant.parse("2026-06-23T04:00:00Z");

    @Test
    void signsAndParsesRolesAndRegisteredClaims() {
        JwtTokenService service = tokenService(SECRET, NOW, Duration.ofHours(2));

        String token = service.createToken(new SecurityPrincipal("user-1", Set.of(UserRole.RIDER, UserRole.ADMIN)));
        JwtToken parsed = service.parse(token);

        assertThat(parsed.principal().userId()).isEqualTo("user-1");
        assertThat(parsed.principal().roles()).containsExactlyInAnyOrder(UserRole.RIDER, UserRole.ADMIN);
        assertThat(parsed.subject()).isEqualTo("user-1");
        assertThat(parsed.tokenId()).isNotBlank();
        assertThat(parsed.issuedAt()).isEqualTo(NOW);
        assertThat(parsed.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void rejectsExpiredAndInvalidSignatureTokens() {
        String token = tokenService(SECRET, NOW, Duration.ofMinutes(5))
            .createToken(new SecurityPrincipal("user-2", Set.of(UserRole.RIDER)));

        assertThatThrownBy(() -> tokenService(SECRET, NOW.plus(Duration.ofMinutes(6)), Duration.ofMinutes(5)).parse(token))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("expired");

        assertThatThrownBy(() -> tokenService(
            "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OQ==",
            NOW,
            Duration.ofMinutes(5)
        ).parse(token))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("invalid");
    }

    private JwtTokenService tokenService(String secret, Instant now, Duration ttl) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setBase64Secret(secret);
        properties.getJwt().setTokenValidity(ttl);
        return new JwtTokenService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
