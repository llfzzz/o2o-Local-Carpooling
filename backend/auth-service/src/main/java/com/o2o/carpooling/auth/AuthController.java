package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.JwtToken;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private static final Set<UserRole> DEFAULT_MOCK_ROLES = Set.of(UserRole.RIDER, UserRole.DRIVER);

    private final JwtTokenService jwtTokenService;

    AuthController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/sms-code")
    SmsCodeResponse sendSmsCode(@RequestBody SmsCodeRequest request) {
        return new SmsCodeResponse(request.phone(), "MOCK-123456", Instant.now().plus(5, ChronoUnit.MINUTES));
    }

    @PostMapping("/login")
    AuthToken login(@RequestBody LoginRequest request) {
        Set<UserRole> roles = request.roles() == null || request.roles().isEmpty() ? DEFAULT_MOCK_ROLES : Set.copyOf(request.roles());
        UserAccount user = new UserAccount(
            "user-" + request.phone(),
            request.phone(),
            roles,
            Instant.now()
        );
        String accessToken = jwtTokenService.createToken(new SecurityPrincipal(user.userId(), user.roles()));
        JwtToken parsedToken = jwtTokenService.parse(accessToken);
        return new AuthToken(accessToken, "Bearer", parsedToken.expiresAt(), user);
    }

    record SmsCodeRequest(String phone) {
    }

    record SmsCodeResponse(String phone, String mockCode, Instant expiresAt) {
    }

    record LoginRequest(String phone, String code, Set<UserRole> roles) {
    }

    record AuthToken(String accessToken, String tokenType, Instant expiresAt, UserAccount user) {
    }
}
