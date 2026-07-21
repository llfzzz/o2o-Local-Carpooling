package com.o2o.carpooling.common.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class BackendFoundationAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withUserConfiguration(TestApplication.class)
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(BackendFoundationAutoConfiguration.class));

    @Test
    void registersMvcFoundationBeans() {
        contextRunner.run(context -> assertThat(context)
            .hasSingleBean(GlobalApiExceptionHandler.class)
            .hasSingleBean(TraceIdFilter.class));
    }

    @Test
    void registersAppAndProviderPropertiesWithFailClosedDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AppProperties.class).hasSingleBean(ProviderProperties.class);
            AppProperties app = context.getBean(AppProperties.class);
            assertThat(app.isDemoMode()).isFalse();
            assertThat(app.getDemo().isControlEnabled()).isFalse();
            assertThat(app.getDemo().isSeedEnabled()).isFalse();
            assertThat(app.getDemo().isLoginCodePeekEnabled()).isFalse();
            assertThat(app.getDemo().isVirtualTripsEnabled()).isFalse();
            ProviderProperties providers = context.getBean(ProviderProperties.class);
            assertThat(providers.getSms().isDemo()).isFalse();
            assertThat(providers.getPayment().isDemo()).isFalse();
        });
    }

    static class TestApplication {
    }
}
