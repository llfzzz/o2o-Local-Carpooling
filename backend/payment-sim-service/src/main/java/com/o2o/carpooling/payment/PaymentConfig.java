package com.o2o.carpooling.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires the payment webhook ingress. The nonce store prefers Redis (cross-instance replay
 * protection) and falls back to in-memory for single-instance/test runs, mirroring the SMS/refresh
 * token stores. The webhook secret is read from {@code providers.payment.webhook-secret}
 * (env {@code PAYMENT_WEBHOOK_SECRET}); a blank secret makes the verifier fail closed.
 */
@Configuration
class PaymentConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    SeenNonceStore redisSeenNonceStore(StringRedisTemplate redisTemplate) {
        return new RedisSeenNonceStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    SeenNonceStore inMemorySeenNonceStore(Clock clock) {
        return new InMemorySeenNonceStore(clock);
    }

    @Bean
    PaymentCallbackVerifier paymentCallbackVerifier(
        @Value("${providers.payment.webhook-secret:}") String webhookSecret,
        @Value("${providers.payment.callback-timestamp-tolerance:PT5M}") Duration timestampTolerance,
        SeenNonceStore nonceStore,
        Clock clock
    ) {
        return new PaymentCallbackVerifier(webhookSecret, nonceStore, timestampTolerance, clock);
    }

    /** Demo-only signer used by the Demo Control console to drive the real ingestion pipeline. */
    @Bean
    PaymentCallbackSigner paymentCallbackSigner(
        @Value("${providers.payment.webhook-secret:}") String webhookSecret,
        Clock clock
    ) {
        return new PaymentCallbackSigner(webhookSecret, clock);
    }
}
