package com.o2o.carpooling.common.foundation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
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
