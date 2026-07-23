package com.o2o.carpooling.gateway;

/**
 * Outcome of a rate-limit check: whether the request is allowed, the current window count, and how
 * many seconds the client should wait before retrying (the {@code Retry-After} value — from the real
 * Redis window remainder when the distributed limiter is used).
 */
record RateLimitDecision(boolean allowed, long currentCount, long retryAfterSeconds) {
}
