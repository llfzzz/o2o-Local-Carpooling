package com.o2o.carpooling.common.foundation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@AutoConfiguration
@EnableConfigurationProperties({SecurityProperties.class, AppProperties.class, ProviderProperties.class})
public class BackendFoundationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenService jwtTokenService(SecurityProperties properties, Clock clock) {
        return new JwtTokenService(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebFluxApiErrorWriter webFluxApiErrorWriter() {
        return new WebFluxApiErrorWriter();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "security.rate-limit", name = "backend", havingValue = "redis")
    public FixedWindowRateLimiter redisFixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisFixedWindowRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public FixedWindowRateLimiter inMemoryFixedWindowRateLimiter(Clock clock) {
        return new InMemoryFixedWindowRateLimiter(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public GlobalApiExceptionHandler globalApiExceptionHandler() {
        return new GlobalApiExceptionHandler();
    }
}
