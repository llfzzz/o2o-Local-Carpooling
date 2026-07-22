package com.o2o.carpooling.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_PREFIX = "refresh:token:";
    private static final String FAMILY_PREFIX = "refresh:family:";

    // Compare-and-swap the family's current-token pointer and write the new token in one atomic
    // server-side step. KEYS[1]=family key, KEYS[2]=new token key; ARGV[1]=expected current hash,
    // ARGV[2]=new token hash, ARGV[3]=ttl seconds, ARGV[4]=new token value (userId|familyId).
    private static final DefaultRedisScript<String> ROTATE = new DefaultRedisScript<>("""
        local current = redis.call('get', KEYS[1])
        if not current then
          return 'MISSING'
        end
        if current ~= ARGV[1] then
          redis.call('del', KEYS[1])
          return 'REUSE'
        end
        redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3])
        redis.call('set', KEYS[2], ARGV[4], 'EX', ARGV[3])
        return 'ROTATED'
        """, String.class);

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
    public RotationResult rotate(String expectedCurrentHash, String newTokenHash, String userId, String familyId,
                                 Duration ttl) {
        String outcome = redis.execute(
            ROTATE,
            List.of(FAMILY_PREFIX + familyId, TOKEN_PREFIX + newTokenHash),
            expectedCurrentHash,
            newTokenHash,
            Long.toString(Math.max(1, ttl.toSeconds())),
            userId + "|" + familyId
        );
        if ("ROTATED".equals(outcome)) {
            return RotationResult.ROTATED;
        }
        if ("REUSE".equals(outcome)) {
            return RotationResult.REUSE_DETECTED;
        }
        return RotationResult.FAMILY_MISSING;
    }

    @Override
    public void deleteFamily(String familyId) {
        redis.delete(FAMILY_PREFIX + familyId);
    }
}
