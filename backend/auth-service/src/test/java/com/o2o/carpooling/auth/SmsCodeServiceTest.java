package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.foundation.AppProperties;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.InMemoryFixedWindowRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SmsCodeServiceTest {

    private static final String PHONE = "13800000000";

    private final TestClock clock = new TestClock(Instant.parse("2026-07-01T00:00:00Z"));
    private final InMemorySmsCodeStore store = new InMemorySmsCodeStore(clock);
    private final InMemoryDemoLoginCodeStore demoLoginCodes = new InMemoryDemoLoginCodeStore(clock);
    private final InMemoryFixedWindowRateLimiter rateLimiter = new InMemoryFixedWindowRateLimiter(clock);
    private final SmsCodeProperties properties = new SmsCodeProperties();
    private final AppProperties app = new AppProperties();
    private final DemoLoginCodeSmsProvider provider = new DemoLoginCodeSmsProvider(demoLoginCodes);

    private SmsCodeService service(List<SmsProvider> providers) {
        return new SmsCodeService(store, demoLoginCodes, providers, rateLimiter, properties, app, clock);
    }

    private SmsCodeService demoService() {
        app.getDemo().setLoginCodePeekEnabled(true);
        return service(List.of(provider));
    }

    @Test
    void issuesAndVerifiesSingleUseCode() {
        SmsCodeService service = service(List.of(provider));
        service.requestCode(PHONE);

        assertThatCode(() -> service.verify(PHONE, provider.lastCode)).doesNotThrowAnyException();
        // single-use: the code is consumed
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.verify(PHONE, provider.lastCode))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("SMS_CODE_EXPIRED"));
    }

    @Test
    void rejectsWrongCode() {
        SmsCodeService service = service(List.of(provider));
        service.requestCode(PHONE);

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.verify(PHONE, "000000"))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("SMS_CODE_INVALID"));
    }

    @Test
    void locksOutAfterTooManyFailedAttempts() {
        SmsCodeService service = service(List.of(provider));
        service.requestCode(PHONE);
        for (int i = 0; i < properties.getMaxVerifyAttempts(); i++) {
            assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.verify(PHONE, "000000"));
        }
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.verify(PHONE, "000000"))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("SMS_CODE_LOCKED"));
    }

    @Test
    void rejectsExpiredCode() {
        SmsCodeService service = service(List.of(provider));
        service.requestCode(PHONE);
        clock.advance(properties.getCodeTtl().plus(Duration.ofSeconds(1)));

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.verify(PHONE, provider.lastCode))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("SMS_CODE_EXPIRED"));
    }

    @Test
    void throttlesIssuancePerPhone() {
        SmsCodeService service = service(List.of(provider));
        for (int i = 0; i < properties.getIssueMaxPerWindow(); i++) {
            service.requestCode(PHONE);
        }
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.requestCode(PHONE))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("SMS_RATE_LIMITED"));
    }

    @Test
    void failsClosedWhenNoProviderConfigured() {
        SmsCodeService service = service(List.of());
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.requestCode(PHONE))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("SMS_PROVIDER_UNCONFIGURED"));
    }

    @Test
    void demoPeekIsDisabledOutsideDemo() {
        SmsCodeService service = service(List.of(provider));
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.peekDemoLoginCode(PHONE, "chg-any"))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("DEMO_ENDPOINT_DISABLED"));
    }

    @Test
    void demoPeekReturnsCodeOnlyForTheMatchingChallenge() {
        SmsCodeService service = demoService();
        SmsCodeService.IssuedChallenge challenge = service.requestCode(PHONE);

        assertThat(service.peekDemoLoginCode(PHONE, challenge.challengeId()))
            .hasValueSatisfying(peeked -> assertThat(peeked.code()).isEqualTo(provider.lastCode));
        // Wrong or absent challenge is indistinguishable from "no code issued yet".
        assertThat(service.peekDemoLoginCode(PHONE, "chg-ffffffffffffffffffffffffffffffff")).isEmpty();
        assertThat(service.peekDemoLoginCode(PHONE, null)).isEmpty();
    }

    @Test
    void demoPeekChallengeFromAPreviousIssueIsStaleAfterReissue() {
        SmsCodeService service = demoService();
        SmsCodeService.IssuedChallenge first = service.requestCode(PHONE);
        SmsCodeService.IssuedChallenge second = service.requestCode(PHONE);

        assertThat(service.peekDemoLoginCode(PHONE, first.challengeId())).isEmpty();
        assertThat(service.peekDemoLoginCode(PHONE, second.challengeId())).isPresent();
    }

    @Test
    void demoPlaintextIsDeletedAfterSuccessfulLoginVerify() {
        SmsCodeService service = demoService();
        SmsCodeService.IssuedChallenge challenge = service.requestCode(PHONE);
        service.verify(PHONE, provider.lastCode);

        assertThat(service.peekDemoLoginCode(PHONE, challenge.challengeId())).isEmpty();
    }

    @Test
    void demoPlaintextIsDeletedOnLockout() {
        SmsCodeService service = demoService();
        SmsCodeService.IssuedChallenge challenge = service.requestCode(PHONE);
        for (int i = 0; i <= properties.getMaxVerifyAttempts(); i++) {
            assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.verify(PHONE, "000000"));
        }

        assertThat(service.peekDemoLoginCode(PHONE, challenge.challengeId())).isEmpty();
    }

    @Test
    void demoPlaintextExpiresWithTheCodeTtl() {
        SmsCodeService service = demoService();
        SmsCodeService.IssuedChallenge challenge = service.requestCode(PHONE);
        clock.advance(properties.getCodeTtl().plus(Duration.ofSeconds(1)));

        assertThat(service.peekDemoLoginCode(PHONE, challenge.challengeId())).isEmpty();
    }

    @Test
    void demoPeekIsRateLimitedPerPhone() {
        SmsCodeService service = demoService();
        SmsCodeService.IssuedChallenge challenge = service.requestCode(PHONE);
        for (int i = 0; i < 10; i++) {
            service.peekDemoLoginCode(PHONE, challenge.challengeId());
        }
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.peekDemoLoginCode(PHONE, challenge.challengeId()))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("SMS_RATE_LIMITED"));
    }

    /** Mirrors DemoSmsProvider: delivers into the challenge-bound store, never anywhere else. */
    private static final class DemoLoginCodeSmsProvider implements SmsProvider {
        private final DemoLoginCodeStore store;
        private String lastCode;

        private DemoLoginCodeSmsProvider(DemoLoginCodeStore store) {
            this.store = store;
        }

        @Override
        public void send(SmsSendCommand command) {
            this.lastCode = command.code();
            store.save(command.phone(), command.correlationId(), command.code(), command.ttl());
        }
    }

    private static final class TestClock extends Clock {
        private Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
