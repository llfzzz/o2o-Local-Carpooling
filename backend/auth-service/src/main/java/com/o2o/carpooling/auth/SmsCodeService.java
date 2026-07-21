package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.foundation.AppProperties;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Server-side SMS login-code lifecycle: generation, hashed single-use storage with TTL,
 * per-phone issuance throttling, and verification with attempt lockout. The plaintext code is
 * only ever delivered through the {@link SmsProvider}; it never enters the notification inbox.
 * Each issuance mints an opaque login challengeId — the demo login-page peek must present it,
 * so a peek is bound to the request that produced the code.
 */
@Service
class SmsCodeService {

    private static final Pattern PHONE = Pattern.compile("\\d{6,15}");
    private static final int PEEK_MAX_PER_WINDOW = 10;
    private static final Duration PEEK_WINDOW = Duration.ofMinutes(5);

    private final SmsCodeStore store;
    private final DemoLoginCodeStore demoLoginCodes;
    private final List<SmsProvider> smsProviders;
    private final FixedWindowRateLimiter rateLimiter;
    private final SmsCodeProperties properties;
    private final AppProperties app;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    SmsCodeService(
        SmsCodeStore store,
        DemoLoginCodeStore demoLoginCodes,
        List<SmsProvider> smsProviders,
        FixedWindowRateLimiter rateLimiter,
        SmsCodeProperties properties,
        AppProperties app,
        Clock clock
    ) {
        this.store = store;
        this.demoLoginCodes = demoLoginCodes;
        this.smsProviders = smsProviders;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.app = app;
        this.clock = clock;
    }

    /** Generate, store (hashed), and deliver a one-time code. Returns the login challenge. */
    IssuedChallenge requestCode(String phone) {
        String normalized = normalize(phone);
        if (!rateLimiter.allow("sms:issue:" + normalized, properties.getIssueMaxPerWindow(), properties.getIssueWindow())) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "SMS_RATE_LIMITED",
                "验证码请求过于频繁，请稍后再试");
        }
        String code = randomNumericCode(properties.getCodeLength());
        String challengeId = "chg-" + randomHex();
        store.save(normalized, hash(normalized, code), properties.getCodeTtl());
        provider().send(new SmsProvider.SmsSendCommand(normalized, userId(normalized), code,
            properties.getCodeTtl(), challengeId));
        return new IssuedChallenge(challengeId, clock.instant().plus(properties.getCodeTtl()));
    }

    /** Verify a code: enforces existence/TTL, attempt lockout, constant-time match, single-use. */
    void verify(String phone, String code) {
        String normalized = normalize(phone);
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "SMS_CODE_INVALID", "验证码错误");
        }
        String storedHash = store.findHash(normalized)
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "SMS_CODE_EXPIRED",
                "验证码不存在或已过期，请重新获取"));
        long attempts = store.incrementAttempts(normalized, properties.getCodeTtl());
        if (attempts > properties.getMaxVerifyAttempts()) {
            store.clear(normalized);
            demoLoginCodes.clear(normalized);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "SMS_CODE_LOCKED",
                "验证失败次数过多，请重新获取验证码");
        }
        if (!MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.UTF_8),
            hash(normalized, code).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "SMS_CODE_INVALID", "验证码错误");
        }
        store.clear(normalized);
        demoLoginCodes.clear(normalized);
    }

    /**
     * Demo-only login-page peek: returns the current code only for the (phone, challengeId) pair
     * minted by the matching {@link #requestCode}. A wrong or stale challenge is indistinguishable
     * from "no code issued yet". Never reads or writes the notification inbox.
     */
    Optional<DemoLoginCodeStore.PeekedCode> peekDemoLoginCode(String phone, String challengeId) {
        if (!app.getDemo().isLoginCodePeekEnabled()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "DEMO_ENDPOINT_DISABLED", "not found");
        }
        String normalized = normalize(phone);
        if (!rateLimiter.allow("sms:peek:" + normalized, PEEK_MAX_PER_WINDOW, PEEK_WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "SMS_RATE_LIMITED",
                "查询过于频繁，请稍后再试");
        }
        if (!StringUtils.hasText(challengeId)) {
            return Optional.empty();
        }
        return demoLoginCodes.find(normalized, challengeId);
    }

    String userId(String phone) {
        return "user-" + phone;
    }

    private SmsProvider provider() {
        return smsProviders.stream().findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_PROVIDER_UNCONFIGURED",
                "no SMS provider configured"));
    }

    private String normalize(String phone) {
        String trimmed = phone == null ? "" : phone.trim();
        if (!PHONE.matcher(trimmed).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "手机号格式不正确");
        }
        return trimmed;
    }

    private String randomNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private String randomHex() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hash(String phone, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((phone + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to hash sms code", exception);
        }
    }

    record IssuedChallenge(String challengeId, Instant expiresAt) {
    }
}
