package com.o2o.carpooling.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.refresh")
public class RefreshTokenProperties {

    private Duration tokenValidity = Duration.ofDays(7);

    public Duration getTokenValidity() {
        return tokenValidity;
    }

    public void setTokenValidity(Duration tokenValidity) {
        this.tokenValidity = tokenValidity;
    }
}
