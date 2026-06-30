package com.o2o.carpooling.common.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the shared {@code carpooling-providers.yml} that every service imports: the demo
 * document must turn demo mode and demo providers on, while staging/production must be
 * fail-closed (demo off, demo affordances off, provider types not "demo").
 */
class CarpoolingProvidersYamlTest {

    private static final String ON_PROFILE = "spring.config.activate.on-profile";

    @Test
    void demoProfileEnablesDemoModeAndDemoProviders() throws IOException {
        PropertySource<?> demo = documentFor("demo");
        Binder binder = new Binder(ConfigurationPropertySources.from(demo));

        AppProperties app = binder.bind("app", AppProperties.class).get();
        ProviderProperties providers = binder.bind("providers", ProviderProperties.class).get();

        assertThat(app.isDemoMode()).isTrue();
        assertThat(app.getDemo().isInboxEnabled()).isTrue();
        assertThat(app.getDemo().isControlEnabled()).isTrue();
        // seed/reset stays off even in demo until explicitly enabled.
        assertThat(app.getDemo().isSeedEnabled()).isFalse();
        assertThat(providers.getSms().isDemo()).isTrue();
        assertThat(providers.getOcr().isDemo()).isTrue();
        assertThat(providers.getPayment().isDemo()).isTrue();
        assertThat(providers.getIdentity().isDemo()).isTrue();
        assertThat(providers.getMap().isDemo()).isTrue();
        assertThat(providers.getNotification().isDemo()).isTrue();
    }

    @Test
    void stagingProfileIsFailClosed() throws IOException {
        PropertySource<?> staging = documentFor("staging");
        Binder binder = new Binder(ConfigurationPropertySources.from(staging));

        AppProperties app = binder.bind("app", AppProperties.class).orElseGet(AppProperties::new);
        ProviderProperties providers = binder.bind("providers", ProviderProperties.class).orElseGet(ProviderProperties::new);

        assertThat(app.isDemoMode()).isFalse();
        assertThat(app.getDemo().isInboxEnabled()).isFalse();
        assertThat(app.getDemo().isControlEnabled()).isFalse();
        assertThat(app.getDemo().isSeedEnabled()).isFalse();
        // No demo provider may be active outside the demo profile.
        assertThat(providers.getSms().isDemo()).isFalse();
        assertThat(providers.getPayment().isDemo()).isFalse();
        assertThat(providers.getIdentity().isDemo()).isFalse();
        assertThat(providers.getNotification().isDemo()).isFalse();
    }

    private PropertySource<?> documentFor(String profile) throws IOException {
        List<PropertySource<?>> documents =
            new YamlPropertySourceLoader().load("carpooling-providers", new ClassPathResource("carpooling-providers.yml"));
        return documents.stream()
            .filter(source -> profile.equals(source.getProperty(ON_PROFILE)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no document for profile " + profile));
    }
}
