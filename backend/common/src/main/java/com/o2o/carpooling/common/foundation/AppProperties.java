package com.o2o.carpooling.common.foundation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-service application switches. {@code demoMode} and the {@code demo.*} flags gate
 * interactive demo affordances (Demo Inbox, Demo Control, seed/reset) that must never be
 * enabled outside the {@code demo} profile. Defaults are fail-closed: everything off.
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
        /** User-scoped Demo Inbox read API. */
        private boolean inboxEnabled = false;
        /** Operator/admin Demo Control API that drives mock-provider outcomes. */
        private boolean controlEnabled = false;
        /** Demo seed/reset endpoints. Requires demoMode AND this flag (double-fenced). */
        private boolean seedEnabled = false;

        public boolean isInboxEnabled() {
            return inboxEnabled;
        }

        public void setInboxEnabled(boolean inboxEnabled) {
            this.inboxEnabled = inboxEnabled;
        }

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
    }
}
