package com.o2o.carpooling.common.foundation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-service application switches. {@code demoMode} and the {@code demo.*} flags gate
 * interactive demo affordances (Demo Control, seed/reset, login-code peek, virtual trips) that
 * must never be enabled outside the {@code demo} profile. Defaults are fail-closed: everything
 * off. (The user Message Center at /api/inbox is a production feature and is not demo-gated.)
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private boolean demoMode = false;
    private Demo demo = new Demo();

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public Demo getDemo() {
        return demo;
    }

    public void setDemo(Demo demo) {
        this.demo = demo;
    }

    public static class Demo {
        /** Operator/admin Demo Control API that drives mock-provider outcomes. */
        private boolean controlEnabled = false;
        /** Demo seed/reset endpoints. Requires demoMode AND this flag (double-fenced). */
        private boolean seedEnabled = false;
        /** Login-page-only demo peek of the current SMS login code (challenge-bound). */
        private boolean loginCodePeekEnabled = false;
        /** Demo virtual-trip generation endpoints in trip-service. */
        private boolean virtualTripsEnabled = false;

        public boolean isControlEnabled() {
            return controlEnabled;
        }

        public void setControlEnabled(boolean controlEnabled) {
            this.controlEnabled = controlEnabled;
        }

        public boolean isSeedEnabled() {
            return seedEnabled;
        }

        public void setSeedEnabled(boolean seedEnabled) {
            this.seedEnabled = seedEnabled;
        }

        public boolean isLoginCodePeekEnabled() {
            return loginCodePeekEnabled;
        }

        public void setLoginCodePeekEnabled(boolean loginCodePeekEnabled) {
            this.loginCodePeekEnabled = loginCodePeekEnabled;
        }

        public boolean isVirtualTripsEnabled() {
            return virtualTripsEnabled;
        }

        public void setVirtualTripsEnabled(boolean virtualTripsEnabled) {
            this.virtualTripsEnabled = virtualTripsEnabled;
        }
    }
}
