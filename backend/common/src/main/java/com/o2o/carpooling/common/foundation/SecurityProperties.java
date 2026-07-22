package com.o2o.carpooling.common.foundation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class Jwt {
        private String header = "Authorization";
        private String tokenStartWith = "Bearer";
        private String issuer = "o2o-local-carpooling";
        // No default secret: must be supplied via environment configuration (JWT_BASE64_SECRET).
        // A blank value fails closed when JwtTokenService is constructed (gateway/auth only).
        private String base64Secret = "";
        private Duration tokenValidity = Duration.ofHours(2);

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public String getTokenStartWith() {
            return tokenStartWith;
        }

        public void setTokenStartWith(String tokenStartWith) {
            this.tokenStartWith = tokenStartWith;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getBase64Secret() {
            return base64Secret;
        }

        public void setBase64Secret(String base64Secret) {
            this.base64Secret = base64Secret;
        }

        public Duration getTokenValidity() {
            return tokenValidity;
        }

        public void setTokenValidity(Duration tokenValidity) {
            this.tokenValidity = tokenValidity;
        }
    }

    public static class RateLimit {
        private String backend = "memory";
        private Window auth = new Window(20, Duration.ofSeconds(60));
        private Window api = new Window(120, Duration.ofSeconds(60));
        /**
         * Separate, tighter budget for map endpoints. Autocomplete fires on every debounced
         * keystroke and each call costs external provider quota, so map traffic gets its own
         * bucket rather than sharing the general API allowance.
         */
        private Window map = new Window(60, Duration.ofSeconds(60));
        /**
         * Non-loopback reverse-proxy / load-balancer addresses whose {@code X-Real-IP} /
         * {@code X-Forwarded-For} may be trusted to carry the real client IP. Loopback peers (the
         * on-host nginx that fronts the gateway) are always trusted; add other proxy IPs here. Empty
         * by default. Without this, all traffic behind a proxy shares one bucket keyed by the proxy IP.
         */
        private List<String> trustedProxies = new ArrayList<>();

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public List<String> getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(List<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }

        public Window getAuth() {
            return auth;
        }

        public void setAuth(Window auth) {
            this.auth = auth;
        }

        public Window getApi() {
            return api;
        }

        public void setApi(Window api) {
            this.api = api;
        }

        public Window getMap() {
            return map;
        }

        public void setMap(Window map) {
            this.map = map;
        }
    }

    public static class Window {
        private int count;
        private Duration period;

        public Window() {
        }

        public Window(int count, Duration period) {
            this.count = count;
            this.period = period;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public Duration getPeriod() {
            return period;
        }

        public void setPeriod(Duration period) {
            this.period = period;
        }
    }
}
