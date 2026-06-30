package com.o2o.carpooling.common.foundation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

/**
 * Enforces, at startup in every service, that demo mode and demo-only affordances can never be
 * active outside the {@code demo} profile. Fails the context (fail-closed) on any illegal
 * combination so a demo-configured build can never accidentally run as staging/production and
 * vice versa.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code app.demo-mode=true} is forbidden under {@code staging}/{@code production}.</li>
 *   <li>Any demo affordance (inbox/control/seed) is forbidden under {@code staging}/{@code production}.</li>
 *   <li>Any demo affordance requires {@code app.demo-mode=true} (double-fence for seed/reset).</li>
 * </ul>
 */
public class DemoModeGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DemoModeGuard.class);

    private final AppProperties app;
    private final Environment environment;

    public DemoModeGuard(AppProperties app, Environment environment) {
        this.app = app;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> profiles = Set.of(environment.getActiveProfiles());
        boolean hardenedProfile = profiles.contains("production") || profiles.contains("staging");
        AppProperties.Demo demo = app.getDemo();
        boolean anyDemoAffordance = demo.isInboxEnabled() || demo.isControlEnabled() || demo.isSeedEnabled();

        if (hardenedProfile && app.isDemoMode()) {
            throw new IllegalStateException(refuse(profiles,
                "app.demo-mode=true is forbidden outside the demo profile"));
        }
        if (hardenedProfile && anyDemoAffordance) {
            throw new IllegalStateException(refuse(profiles,
                "demo affordances (inbox/control/seed) are forbidden outside the demo profile"));
        }
        if (anyDemoAffordance && !app.isDemoMode()) {
            throw new IllegalStateException(refuse(profiles,
                "demo affordances require app.demo-mode=true"));
        }
        log.info("DemoModeGuard passed: profiles={}, demoMode={}", Arrays.toString(environment.getActiveProfiles()), app.isDemoMode());
    }

    private String refuse(Set<String> profiles, String reason) {
        return "Refusing to start with profiles " + profiles + ": " + reason + ".";
    }
}
