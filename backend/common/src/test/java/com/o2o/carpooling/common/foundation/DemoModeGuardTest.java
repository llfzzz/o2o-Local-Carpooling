package com.o2o.carpooling.common.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoModeGuardTest {

    @Test
    void rejectsDemoModeUnderProduction() {
        AppProperties app = new AppProperties();
        app.setDemoMode(true);

        assertThatThrownBy(guard(app, "production")::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("demo-mode");
    }

    @Test
    void rejectsDemoAffordanceUnderStaging() {
        AppProperties app = new AppProperties();
        app.getDemo().setInboxEnabled(true);

        assertThatThrownBy(guard(app, "staging")::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("demo affordances");
    }

    @Test
    void rejectsDemoAffordanceWithoutDemoMode() {
        AppProperties app = new AppProperties();
        app.getDemo().setSeedEnabled(true); // demo-mode stays false

        assertThatThrownBy(guard(app, "demo")::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("require app.demo-mode=true");
    }

    @Test
    void allowsDemoProfileWithDemoModeAndAffordances() {
        AppProperties app = new AppProperties();
        app.setDemoMode(true);
        app.getDemo().setInboxEnabled(true);
        app.getDemo().setControlEnabled(true);

        assertThatCode(guard(app, "demo")::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void allowsHardenedProfileWithEverythingOff() {
        assertThatCode(guard(new AppProperties(), "production")::afterPropertiesSet).doesNotThrowAnyException();
    }

    private DemoModeGuard guard(AppProperties app, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new DemoModeGuard(app, environment);
    }
}
