package com.o2o.carpooling.auth;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_PREFIX = "refresh:token:";
    private static final String FAMILY_PREFIX = "refresh:family:";

    private final StringRedisTemplate redis;

    RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveToken(String tokenHash, String userId, String familyId, Duration ttl) {
        redis.opsForValue().set(TOKEN_PREFIX + tokenHash, userId + "|" + familyId, ttl);
    }

    @Override
    public void setFamilyCurrent(String familyId, String tokenHash, Duration ttl) {
        redis.opsForValue().set(FAMILY_PREFIX + familyId, tokenHash, ttl);
    }

    @Override
    public Optional<RefreshRecord> findToken(String tokenHash) {
        String value = redis.opsForValue().get(TOKEN_PREFIX + tokenHash);
        if (value == null) {
            return Optional.empty();
        }
        int separator = value.indexOf('|');
        if (separator < 0) {
            return Optional.empty();
        }
        return Optional.of(new RefreshRecord(value.substring(0, separator), value.substring(separator + 1)));
    }

    @Override
    public Optional<String> familyCurrent(String familyId) {
        return Optional.ofNullable(redis.opsForValue().get(FAMILY_PREFIX + familyId));
    }

    @Override
    public void deleteFamily(String familyId) {
        redis.delete(FAMILY_PREFIX + familyId);
    }
}
