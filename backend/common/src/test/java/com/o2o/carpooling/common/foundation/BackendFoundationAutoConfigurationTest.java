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

    static class TestApplication {
    }
}
