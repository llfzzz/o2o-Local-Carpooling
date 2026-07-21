package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.foundation.JwtToken;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final SmsCodeService smsCodeService;
    private final UserAccounts userAccounts;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final DemoEndpoints demoEndpoints;

    AuthController(
        SmsCodeService smsCodeService,
        UserAccounts userAccounts,
        JwtTokenService jwtTokenService,
        RefreshTokenService refreshTokenService,
        DemoEndpoints demoEndpoints
    ) {
        this.smsCodeService = smsCodeService;
        this.userAccounts = userAccounts;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.demoEndpoints = demoEndpoints;
    }

    @PostMapping("/sms-code")
    SmsCodeResponse sendSmsCode(@RequestBody SmsCodeRequest request) {
        SmsCodeService.IssuedChallenge challenge = smsCodeService.requestCode(request.phone());
        // The code is never returned here. In demo mode the login page may peek it via
        // /sms-code/demo-peek by presenting this challengeId (the challenge is opaque and
        // worthless without the matching phone).
        return new SmsCodeResponse(maskPhone(request.phone()), challenge.challengeId(),
            challenge.expiresAt(), "验证码已发送");
    }

    /**
     * Demo-only login-page peek: POST (phone stays out of URLs/access logs), bound to the
     * challengeId minted by the matching sms-code request. 404 outside demo. A wrong or stale
     * challenge returns the same "not yet received" shape as no code at all.
     */
    @PostMapping("/sms-code/demo-peek")
    DemoPeekResponse demoPeek(@RequestBody DemoPeekRequest request) {
        return smsCodeService.peekDemoLoginCode(request.phone(), request.challengeId())
            .map(peeked -> new DemoPeekResponse(maskPhone(request.phone()), peeked.code(),
                peeked.expiresAt(), "演示模式：验证码仅在登录页临时可见，登录后即失效"))
            .orElseGet(() -> new DemoPeekResponse(maskPhone(request.phone()), null, null,
                "尚未收到验证码，请先获取"));
    }

    @PostMapping("/login")
    AuthToken login(@RequestBody LoginRequest request) {
        smsCodeService.verify(request.phone(), request.code());
        UserAccount user = userAccounts.getOrCreate(smsCodeService.userId(request.phone()), request.phone());
        return issueTokens(user);
    }

    /**
     * Demo-only: mint an operator (OPERATOR + ADMIN) session in one call, so the admin console and
     * the Demo Control endpoints are usable without a real operator-provisioning flow. Double-gated
     * by DemoEndpoints.requireSeed() (demo profile + app.demo.seed-enabled); 404 otherwise.
     */
    @PostMapping("/demo/operator-session")
    AuthToken demoOperatorSession(@RequestBody(required = false) DemoOperatorRequest request) {
        demoEndpoints.requireSeed();
        String phone = request != null && request.phone() != null && !request.phone().isBlank()
            ? request.phone() : "13900000000";
        UserAccount operator = userAccounts.seedOperator(smsCodeService.userId(phone), phone);
        return issueTokens(operator);
    }

    @PostMapping("/refresh")
    RefreshResponse refresh(@RequestBody RefreshRequest request) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(request.refreshToken());
        UserAccount user = userAccounts.require(rotated.userId());
        String accessToken = jwtTokenService.createToken(new SecurityPrincipal(user.userId(), user.roles()));
        JwtToken parsed = jwtTokenService.parse(accessToken);
        return new RefreshResponse(accessToken, "Bearer", parsed.expiresAt(), rotated.token(), rotated.expiresAt());
    }

    @PostMapping("/logout")
    void logout(@RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthToken issueTokens(UserAccount user) {
        String accessToken = jwtTokenService.createToken(new SecurityPrincipal(user.userId(), user.roles()));
        JwtToken parsed = jwtTokenService.parse(accessToken);
        RefreshTokenService.IssuedToken refresh = refreshTokenService.issue(user.userId());
        return new AuthToken(accessToken, "Bearer", parsed.expiresAt(), refresh.token(), refresh.expiresAt(), user);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    record SmsCodeRequest(String phone) {
    }

    record SmsCodeResponse(String phoneMasked, String challengeId, Instant expiresAt, String message) {
    }

    record DemoPeekRequest(String phone, String challengeId) {
    }

    record DemoPeekResponse(String phoneMasked, String code, Instant expiresAt, String message) {
    }

    // No roles field: roles are server-authoritative and resolved from the user record.
    record LoginRequest(String phone, String code) {
    }

    record DemoOperatorRequest(String phone) {
    }

    record AuthToken(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String refreshToken,
        Instant refreshExpiresAt,
        UserAccount user
    ) {
    }

    record RefreshRequest(String refreshToken) {
    }

    record RefreshResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String refreshToken,
        Instant refreshExpiresAt
    ) {
    }
}
