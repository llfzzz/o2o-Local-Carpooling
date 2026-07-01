package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.foundation.AppProperties;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Per-request gate for this service's demo-only endpoints. Returns 404 (not 403) when the flag is
 * off, so a demo endpoint is indistinguishable from a non-existent one outside the demo profile.
 * DemoModeGuard already guarantees these flags can only be true under the demo profile. (Mirrors
 * the same helper in notification-service — each service owns its own demo gating.)
 */
@Component
class DemoEndpoints {

    private final AppProperties app;

    DemoEndpoints(AppProperties app) {
        this.app = app;
    }

    void requireControl() {
        if (!app.getDemo().isControlEnabled()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "DEMO_ENDPOINT_DISABLED", "not found");
        }
    }
}
