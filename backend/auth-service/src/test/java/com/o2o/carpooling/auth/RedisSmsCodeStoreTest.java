package com.o2o.carpooling.auth;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Locks in that the attempt counter is bumped with a single atomic Lua script, never INCR-then-EXPIRE. */
class RedisSmsCodeStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void incrementAttemptsRunsOneAtomicScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(2L).when(redis).execute(any(RedisScript.class), anyList(), anyString());
        RedisSmsCodeStore store = new RedisSmsCodeStore(redis);

        long attempts = store.incrementAttempts("13800000000", Duration.ofMinutes(5));

        assertThat(attempts).isEqualTo(2L);
        // One server-side script call — not a separate opsForValue().increment()/expire() pair that
        // could crash between round-trips and leave a no-TTL, un-clearable lockout counter.
        verify(redis).execute(any(RedisScript.class), anyList(), anyString());
        verify(redis, never()).opsForValue();
    }
}
