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
        // seed/reset is enabled under the demo profile (S26: demo operator session + reset);
        // DemoModeGuard still guarantees it can never be true outside demo.
        assertThat(app.getDemo().isSeedEnabled()).isTrue();
        assertThat(providers.getSms().isDemo()).isTrue();
        assertThat(providers.getOcr().isDemo()).isTrue();
        assertThat(providers.getPayment().isDemo()).isTrue();
        assertThat(providers.getIdentity().isDemo()).isTrue();
        assertThat(providers.getNotification().isDemo()).isTrue();
        // Map is asserted separately: it is the one provider whose type is an env placeholder, and
        // this Binder has no Environment to resolve it against.
    }

    @Test
    void demoProfileDefaultsMapToDemoButLetsItBeSwitchedToTheRealVendor() throws IOException {
        // Without the placeholder, MAP_PROVIDER=amap is silently ignored under the demo profile:
        // the operator sees fixture data while believing the map is live — the worst failure mode
        // available, because it looks like success. The `:demo` default keeps it fail-safe.
        PropertySource<?> demo = documentFor("demo");

        assertThat(demo.getProperty("providers.map.type")).isEqualTo("${MAP_PROVIDER:demo}");
        // Every other provider stays pinned to demo — only the map may point at a real vendor,
        // because it is the only one that costs quota rather than money or real user impact.
        assertThat(demo.getProperty("providers.sms.type")).isEqualTo("demo");
        assertThat(demo.getProperty("providers.payment.type")).isEqualTo("demo");
        assertThat(demo.getProperty("providers.identity.type")).isEqualTo("demo");
        assertThat(demo.getProperty("providers.ocr.type")).isEqualTo("demo");
        assertThat(demo.getProperty("providers.notification.type")).isEqualTo("demo");
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
