package com.o2o.carpooling.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Redis-backed code store so issuance/verification/lockout work across multiple instances. */
class RedisSmsCodeStore implements SmsCodeStore {

    private static final String CODE_PREFIX = "sms:code:";
    private static final String ATTEMPTS_PREFIX = "sms:code:attempts:";

    // Increment the attempt counter and set its TTL in a single atomic server-side step (same
    // pattern as RedisFixedWindowRateLimiter). A plain INCR-then-EXPIRE could crash between the
    // two round-trips, leaving a counter with no TTL — a permanent, un-clearable per-phone lockout.
    private static final DefaultRedisScript<Long> INCREMENT_ATTEMPTS = new DefaultRedisScript<>("""
        local current = redis.call('incr', KEYS[1])
        if tonumber(current) == 1 then
          redis.call('expire', KEYS[1], ARGV[1])
        end
        return current
        """, Long.class);

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
        Long count = redis.execute(
            INCREMENT_ATTEMPTS,
            List.of(ATTEMPTS_PREFIX + phone),
            Long.toString(Math.max(1, ttl.toSeconds()))
        );
        return count == null ? Long.MAX_VALUE : count;
    }

    @Override
    public void clear(String phone) {
        redis.delete(CODE_PREFIX + phone);
        redis.delete(ATTEMPTS_PREFIX + phone);
    }
}
