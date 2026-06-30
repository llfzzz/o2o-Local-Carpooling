package com.o2o.carpooling.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SmsCodeProperties.class)
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
}
