package com.o2o.carpooling.common.foundation;

import java.time.Duration;

public interface FixedWindowRateLimiter {

    boolean allow(String key, int limit, Duration window);
}
