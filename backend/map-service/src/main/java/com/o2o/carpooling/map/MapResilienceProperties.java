package com.o2o.carpooling.map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "map")
public class MapResilienceProperties {

    private final RouteCache routeCache = new RouteCache();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    public RouteCache getRouteCache() {
        return routeCache;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public static class RouteCache {

        private boolean enabled = true;
        private Duration freshTtl = Duration.ofMinutes(30);
        private Duration staleIfError = Duration.ofHours(24);
        private final Redis redis = new Redis();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getFreshTtl() {
            return freshTtl;
        }

        public void setFreshTtl(Duration freshTtl) {
            this.freshTtl = freshTtl;
        }

        public Duration getStaleIfError() {
            return staleIfError;
        }

        public void setStaleIfError(Duration staleIfError) {
            this.staleIfError = staleIfError;
        }

        public Redis getRedis() {
            return redis;
        }
    }

    /**
     * Optional Redis read-cache layer in front of the durable MySQL route snapshot. Disabled by
     * default so the low-memory prod demo host is unaffected; a Compose overlay / staging profile
     * enables it. Cache loss is always safe — the MySQL snapshot stays authoritative.
     */
    public static class Redis {

        private boolean enabled = false;
        /** Base Redis TTL. Always additionally capped by the source snapshot's remaining freshness. */
        private Duration baseTtl = Duration.ofMinutes(5);
        /** Random extra TTL as a fraction of base (avalanche protection); 0.2 = up to +20%. */
        private double ttlJitter = 0.2;
        /** Values larger than this (serialized bytes) are not cached (big-key protection). */
        private int maxPayloadBytes = 16384;
        /** Distributed cache-fill lease lifetime — longer than a provider call, short enough to recover a crashed owner. */
        private Duration leaseTtl = Duration.ofSeconds(10);
        /** Max time a lease loser waits (rechecking the cache) before falling back to a safe load. */
        private Duration leaseWait = Duration.ofSeconds(2);
        /** Backoff between a loser's cache rechecks. */
        private Duration leaseBackoff = Duration.ofMillis(50);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getBaseTtl() {
            return baseTtl;
        }

        public void setBaseTtl(Duration baseTtl) {
            this.baseTtl = baseTtl;
        }

        public double getTtlJitter() {
            return ttlJitter;
        }

        public void setTtlJitter(double ttlJitter) {
            this.ttlJitter = ttlJitter;
        }

        public int getMaxPayloadBytes() {
            return maxPayloadBytes;
        }

        public void setMaxPayloadBytes(int maxPayloadBytes) {
            this.maxPayloadBytes = maxPayloadBytes;
        }

        public Duration getLeaseTtl() {
            return leaseTtl;
        }

        public void setLeaseTtl(Duration leaseTtl) {
            this.leaseTtl = leaseTtl;
        }

        public Duration getLeaseWait() {
            return leaseWait;
        }

        public void setLeaseWait(Duration leaseWait) {
            this.leaseWait = leaseWait;
        }

        public Duration getLeaseBackoff() {
            return leaseBackoff;
        }

        public void setLeaseBackoff(Duration leaseBackoff) {
            this.leaseBackoff = leaseBackoff;
        }
    }

    public static class CircuitBreaker {

        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 10;
        private float failureRateThreshold = 50;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int permittedCallsInHalfOpenState = 3;

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedCallsInHalfOpenState() {
            return permittedCallsInHalfOpenState;
        }

        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) {
            this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState;
        }
    }
}
