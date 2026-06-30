package com.o2o.carpooling.auth;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/** Redis-backed code store so issuance/verification/lockout work across multiple instances. */
class RedisSmsCodeStore implements SmsCodeStore {

    private static final String CODE_PREFIX = "sms:code:";
    private static final String ATTEMPTS_PREFIX = "sms:code:attempts:";

    private final StringRedisTemplate redis;

    RedisSmsCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String phone, String codeHash, Duration ttl) {
        redis.opsForValue().set(CODE_PREFIX + phone, codeHash, ttl);
        redis.delete(ATTEMPTS_PREFIX + phone);
    }

    @Override
    public Optional<String> findHash(String phone) {
        return Optional.ofNullable(redis.opsForValue().get(CODE_PREFIX + phone));
    }

    @Override
    public long incrementAttempts(String phone, Duration ttl) {
        String key = ATTEMPTS_PREFIX + phone;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttl);
        }
        return count == null ? Long.MAX_VALUE : count;
    }

    @Override
    public void clear(String phone) {
        redis.delete(CODE_PREFIX + phone);
        redis.delete(ATTEMPTS_PREFIX + phone);
    }
}
