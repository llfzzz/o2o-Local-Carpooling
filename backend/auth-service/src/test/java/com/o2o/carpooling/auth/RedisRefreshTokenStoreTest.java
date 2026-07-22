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

/** The atomic compare-and-swap script's return code maps to the right rotation outcome. */
class RedisRefreshTokenStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis);

    @Test
    @SuppressWarnings("unchecked")
    void mapsRotatedScriptResult() {
        doReturn("ROTATED").when(redis)
            .execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString());
        assertThat(store.rotate("old", "new", "user-1", "fam-1", Duration.ofDays(7)))
            .isEqualTo(RefreshTokenStore.RotationResult.ROTATED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsReuseScriptResult() {
        doReturn("REUSE").when(redis)
            .execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString());
        assertThat(store.rotate("old", "new", "user-1", "fam-1", Duration.ofDays(7)))
            .isEqualTo(RefreshTokenStore.RotationResult.REUSE_DETECTED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsMissingScriptResult() {
        doReturn("MISSING").when(redis)
            .execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString());
        assertThat(store.rotate("old", "new", "user-1", "fam-1", Duration.ofDays(7)))
            .isEqualTo(RefreshTokenStore.RotationResult.FAMILY_MISSING);
    }
}
