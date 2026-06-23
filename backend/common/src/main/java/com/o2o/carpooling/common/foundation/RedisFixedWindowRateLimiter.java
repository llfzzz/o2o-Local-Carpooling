package com.o2o.carpooling.common.foundation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

public class RedisFixedWindowRateLimiter implements FixedWindowRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
        local current = redis.call('incr', KEYS[1])
        if tonumber(current) == 1 then
          redis.call('expire', KEYS[1], ARGV[2])
        end
        return current
        """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allow(String key, int limit, Duration window) {
        if (limit <= 0) {
            return false;
        }
        Long current = redisTemplate.execute(
            SCRIPT,
            List.of(key),
            Integer.toString(limit),
            Long.toString(Math.max(1, window.toSeconds()))
        );
        return current != null && current <= limit;
    }
}
