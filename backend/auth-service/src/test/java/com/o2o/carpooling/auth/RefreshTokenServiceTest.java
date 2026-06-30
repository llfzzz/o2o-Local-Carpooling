package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RefreshTokenServiceTest {

    private final TestClock clock = new TestClock(Instant.parse("2026-07-01T00:00:00Z"));
    private final RefreshTokenProperties properties = new RefreshTokenProperties();
    private final RefreshTokenService service =
        new RefreshTokenService(new InMemoryRefreshTokenStore(clock), properties, clock);

    @Test
    void rotatesAndInvalidatesThePreviousToken() {
        RefreshTokenService.IssuedToken issued = service.issue("user-1");

        RefreshTokenService.RotatedToken rotated = service.rotate(issued.token());
        assertThat(rotated.userId()).isEqualTo("user-1");
        assertThat(rotated.token()).isNotEqualTo(issued.token());

        // the new token continues to work
        assertThatCode(() -> service.rotate(rotated.token())).doesNotThrowAnyException();
    }

    @Test
    void detectsReuseOfARotatedTokenAndRevokesTheFamily() {
        RefreshTokenService.IssuedToken issued = service.issue("user-1");
        RefreshTokenService.RotatedToken rotated = service.rotate(issued.token());

        // replaying the original (rotated-away) token is reuse -> family revoked
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.rotate(issued.token()))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("REFRESH_TOKEN_REUSE"));

        // the previously-valid rotated token is now also dead (whole family revoked)
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.rotate(rotated.token()))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void revokeEndsTheSession() {
        RefreshTokenService.IssuedToken issued = service.issue("user-1");
        service.revoke(issued.token());

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.rotate(issued.token()))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void rejectsExpiredToken() {
        RefreshTokenService.IssuedToken issued = service.issue("user-1");
        clock.advance(properties.getTokenValidity().plus(Duration.ofSeconds(1)));

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.rotate(issued.token()))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void rejectsUnknownToken() {
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> service.rotate("not-a-real-token"))
            .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
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
