package com.o2o.carpooling.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({SmsCodeProperties.class, RefreshTokenProperties.class})
class AuthConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    SmsCodeStore redisSmsCodeStore(StringRedisTemplate redisTemplate) {
        return new RedisSmsCodeStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    SmsCodeStore inMemorySmsCodeStore(Clock clock) {
        return new InMemorySmsCodeStore(clock);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    DemoLoginCodeStore redisDemoLoginCodeStore(StringRedisTemplate redisTemplate, Clock clock) {
        return new RedisDemoLoginCodeStore(redisTemplate, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    DemoLoginCodeStore inMemoryDemoLoginCodeStore(Clock clock) {
        return new InMemoryDemoLoginCodeStore(clock);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    RefreshTokenStore redisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        return new RedisRefreshTokenStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    RefreshTokenStore inMemoryRefreshTokenStore(Clock clock) {
        return new InMemoryRefreshTokenStore(clock);
    }
}
