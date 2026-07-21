package com.o2o.carpooling.notification;

import com.o2o.carpooling.common.foundation.AppProperties;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Per-request gate for demo-only endpoints. Returns 404 (rather than 403) when the
 * corresponding flag is off, so demo endpoints are indistinguishable from non-existent ones
 * outside the demo profile. DemoModeGuard already guarantees these flags can only be true under
 * the demo profile.
 */
@Component
class DemoEndpoints {

    private final AppProperties app;

    DemoEndpoints(AppProperties app) {
        this.app = app;
    }

    void requireControl() {
        if (!app.getDemo().isControlEnabled()) {
            throw disabled();
        }
    }

    private BusinessException disabled() {
        return new BusinessException(HttpStatus.NOT_FOUND, "DEMO_ENDPOINT_DISABLED", "not found");
    }
}
