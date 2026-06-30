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
    private final InMemoryFixedWindowRateLimiter rateLimiter = new InMemoryFixedWindowRateLimiter(clock);
    private final SmsCodeProperties properties = new SmsCodeProperties();
    private final CapturingSmsProvider provider = new CapturingSmsProvider();
    private final AppProperties app = new AppProperties();

    private SmsCodeService service(List<SmsProvider> providers) {
        return new SmsCodeService(store, providers, rateLimiter, properties, new StubNotification(), app, clock);
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
    void demoInboxPeekIsDisabledOutsideDemo() {
        SmsCodeService service = service(List.of(provider));
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.peekDemoInbox(PHONE))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("DEMO_ENDPOINT_DISABLED"));
    }

    private static final class CapturingSmsProvider implements SmsProvider {
        private String lastCode;

        @Override
        public void send(SmsSendCommand command) {
            this.lastCode = command.code();
        }
    }

    private static final class StubNotification implements NotificationFeignClient {
        @Override
        public void notify(NotifyRequest request) {
        }

        @Override
        public LatestDelivery latest(String userId, String category) {
            throw new UnsupportedOperationException("not used");
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
