package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";

    @Test
    void loginReturnsSignedJwtWithDefaultMockRoles() {
        JwtTokenService tokenService = tokenService();
        AuthController controller = new AuthController(tokenService);

        AuthController.AuthToken token = controller.login(new AuthController.LoginRequest("13800000000", "123456", null));

        assertThat(token.accessToken()).doesNotStartWith("mock.jwt.");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-06-23T06:00:00Z"));
        assertThat(tokenService.parse(token.accessToken()).principal().roles())
            .containsExactlyInAnyOrder(UserRole.RIDER, UserRole.DRIVER);
    }

    @Test
    void loginSupportsExplicitMockRolesForLocalTesting() {
        JwtTokenService tokenService = tokenService();
        AuthController controller = new AuthController(tokenService);

        AuthController.AuthToken token = controller.login(
            new AuthController.LoginRequest("13800000001", "123456", Set.of(UserRole.ADMIN))
        );

        assertThat(tokenService.parse(token.accessToken()).principal().roles()).containsExactly(UserRole.ADMIN);
    }

    private JwtTokenService tokenService() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setBase64Secret(SECRET);
        properties.getJwt().setTokenValidity(Duration.ofHours(2));
        return new JwtTokenService(properties, Clock.fixed(Instant.parse("2026-06-23T04:00:00Z"), ZoneOffset.UTC));
    }
}
