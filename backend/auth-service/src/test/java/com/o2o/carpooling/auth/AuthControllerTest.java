package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.AppProperties;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";

    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);
    private final UserAccounts userAccounts = mock(UserAccounts.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthController controller = controllerWithSeed(true);

    private AuthController controllerWithSeed(boolean seedEnabled) {
        AppProperties app = new AppProperties();
        app.getDemo().setSeedEnabled(seedEnabled);
        return new AuthController(smsCodeService, userAccounts, tokenService(), refreshTokenService, new DemoEndpoints(app));
    }

    @Test
    void loginVerifiesCodeAndIssuesTokenWithServerAuthoritativeRoles() {
        when(smsCodeService.userId("13800000000")).thenReturn("user-13800000000");
        when(userAccounts.getOrCreate("user-13800000000", "13800000000")).thenReturn(
            new UserAccount("user-13800000000", "13800000000", Set.of(UserRole.RIDER), Instant.parse("2026-07-01T00:00:00Z")));
        when(refreshTokenService.issue("user-13800000000")).thenReturn(
            new RefreshTokenService.IssuedToken("rt-test", Instant.parse("2026-07-08T00:00:00Z")));

        AuthController.AuthToken token = controller.login(new AuthController.LoginRequest("13800000000", "123456"));

        verify(smsCodeService).verify("13800000000", "123456");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.refreshToken()).isEqualTo("rt-test");
        assertThat(tokenService().parse(token.accessToken()).principal().roles()).containsExactly(UserRole.RIDER);
    }

    @Test
    void loginIsRejectedWhenCodeVerificationFails() {
        doThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "SMS_CODE_INVALID", "验证码错误"))
            .when(smsCodeService).verify(any(), any());

        assertThatThrownBy(() -> controller.login(new AuthController.LoginRequest("13800000000", "000000")))
            .isInstanceOf(BusinessException.class);
        verify(userAccounts, never()).getOrCreate(any(), any());
    }

    @Test
    void demoOperatorSessionSeedsOperatorAndIssuesToken() {
        when(smsCodeService.userId("13900000000")).thenReturn("user-13900000000");
        when(userAccounts.seedOperator("user-13900000000", "13900000000")).thenReturn(
            new UserAccount("user-13900000000", "13900000000", Set.of(UserRole.OPERATOR, UserRole.ADMIN),
                Instant.parse("2026-07-01T00:00:00Z")));
        when(refreshTokenService.issue("user-13900000000")).thenReturn(
            new RefreshTokenService.IssuedToken("rt-op", Instant.parse("2026-07-08T00:00:00Z")));

        AuthController.AuthToken token = controller.demoOperatorSession(new AuthController.DemoOperatorRequest(null));

        verify(userAccounts).seedOperator("user-13900000000", "13900000000");
        assertThat(tokenService().parse(token.accessToken()).principal().roles())
            .containsExactlyInAnyOrder(UserRole.OPERATOR, UserRole.ADMIN);
    }

    @Test
    void demoOperatorSessionHiddenWhenSeedDisabled() {
        assertThatThrownBy(() -> controllerWithSeed(false).demoOperatorSession(new AuthController.DemoOperatorRequest(null)))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("DEMO_ENDPOINT_DISABLED"));
        verify(userAccounts, never()).seedOperator(any(), any());
    }

    @Test
    void loginRequestExposesNoClientRolesField() {
        // Regression guard against privilege escalation: LoginRequest must carry only phone + code.
        assertThat(Arrays.stream(AuthController.LoginRequest.class.getRecordComponents())
            .map(RecordComponent::getName))
            .containsExactlyInAnyOrder("phone", "code");
    }

    private JwtTokenService tokenService() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setBase64Secret(SECRET);
        return new JwtTokenService(properties, Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC));
    }
}
