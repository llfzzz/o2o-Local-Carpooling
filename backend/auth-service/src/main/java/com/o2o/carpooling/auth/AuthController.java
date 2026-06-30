package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.foundation.JwtToken;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final SmsCodeService smsCodeService;
    private final UserAccounts userAccounts;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    AuthController(
        SmsCodeService smsCodeService,
        UserAccounts userAccounts,
        JwtTokenService jwtTokenService,
        RefreshTokenService refreshTokenService
    ) {
        this.smsCodeService = smsCodeService;
        this.userAccounts = userAccounts;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/sms-code")
    SmsCodeResponse sendSmsCode(@RequestBody SmsCodeRequest request) {
        Instant expiresAt = smsCodeService.requestCode(request.phone());
        // The code is delivered out of band (demo inbox). It is never returned here.
        return new SmsCodeResponse(maskPhone(request.phone()), expiresAt, "验证码已发送，请在演示收件箱查看");
    }

    @GetMapping("/sms-code/demo-inbox")
    DemoInboxResponse demoInbox(@RequestParam String phone) {
        return smsCodeService.peekDemoInbox(phone)
            .map(latest -> new DemoInboxResponse(maskPhone(phone), latest.maskedPreview(), latest.value(),
                latest.expiresAt(), "演示模式：已为该手机号取出最新验证码"))
            .orElseGet(() -> new DemoInboxResponse(maskPhone(phone), null, null, null, "尚未收到验证码，请先获取"));
    }

    @PostMapping("/login")
    AuthToken login(@RequestBody LoginRequest request) {
        smsCodeService.verify(request.phone(), request.code());
        UserAccount user = userAccounts.getOrCreate(smsCodeService.userId(request.phone()), request.phone());
        return issueTokens(user);
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

    record SmsCodeResponse(String phoneMasked, Instant expiresAt, String message) {
    }

    record DemoInboxResponse(String phoneMasked, String maskedPreview, String code, Instant expiresAt, String message) {
    }

    // No roles field: roles are server-authoritative and resolved from the user record.
    record LoginRequest(String phone, String code) {
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
