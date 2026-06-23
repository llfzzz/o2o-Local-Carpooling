package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.domain.UserRole;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    @PostMapping("/sms-code")
    SmsCodeResponse sendSmsCode(@RequestBody SmsCodeRequest request) {
        return new SmsCodeResponse(request.phone(), "MOCK-123456", Instant.now().plus(5, ChronoUnit.MINUTES));
    }

    @PostMapping("/login")
    AuthToken login(@RequestBody LoginRequest request) {
        UserAccount user = new UserAccount(
            "user-" + request.phone(),
            request.phone(),
            Set.of(UserRole.RIDER, UserRole.DRIVER),
            Instant.now()
        );
        return new AuthToken("mock.jwt." + UUID.randomUUID(), "Bearer", Instant.now().plus(2, ChronoUnit.HOURS), user);
    }

    record SmsCodeRequest(String phone) {
    }

    record SmsCodeResponse(String phone, String mockCode, Instant expiresAt) {
    }

    record LoginRequest(String phone, String code) {
    }

    record AuthToken(String accessToken, String tokenType, Instant expiresAt, UserAccount user) {
    }
}
