package com.o2o.carpooling.auth;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Redis-backed demo login-code store so the login-page peek works across multiple instances. */
class RedisDemoLoginCodeStore implements DemoLoginCodeStore {

    private static final String PREFIX = "sms:demo-login-code:";
    private static final String SEPARATOR = "|";

    private final StringRedisTemplate redis;
    private final Clock clock;

    RedisDemoLoginCodeStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void save(String phone, String challengeId, String code, Duration ttl) {
        redis.opsForValue().set(PREFIX + phone, challengeId + SEPARATOR + code, ttl);
    }

    @Override
    public Optional<PeekedCode> find(String phone, String challengeId) {
        String key = PREFIX + phone;
        String value = redis.opsForValue().get(key);
        if (value == null || challengeId == null) {
            return Optional.empty();
        }
        int separator = value.indexOf(SEPARATOR);
        if (separator <= 0) {
            return Optional.empty();
        }
        String storedChallenge = value.substring(0, separator);
        if (!MessageDigest.isEqual(storedChallenge.getBytes(StandardCharsets.UTF_8),
            challengeId.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        Long ttlSeconds = redis.getExpire(key, TimeUnit.SECONDS);
        var expiresAt = ttlSeconds == null || ttlSeconds < 0
            ? null
            : clock.instant().plusSeconds(ttlSeconds);
        return Optional.of(new PeekedCode(value.substring(separator + 1), expiresAt));
    }

    @Override
    public void clear(String phone) {
        redis.delete(PREFIX + phone);
    }
}
