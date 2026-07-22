package com.o2o.carpooling.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-instance fallback / test implementation of {@link RefreshTokenStore}. */
class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private record Expiring<T>(T value, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Expiring<RefreshRecord>> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Expiring<String>> families = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryRefreshTokenStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void saveToken(String tokenHash, String userId, String familyId, Duration ttl) {
        tokens.put(tokenHash, new Expiring<>(new RefreshRecord(userId, familyId), clock.instant().plus(ttl)));
    }

    @Override
    public void setFamilyCurrent(String familyId, String tokenHash, Duration ttl) {
        families.put(familyId, new Expiring<>(tokenHash, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<RefreshRecord> findToken(String tokenHash) {
        return live(tokens.get(tokenHash), () -> tokens.remove(tokenHash));
    }

    @Override
    public RotationResult rotate(String expectedCurrentHash, String newTokenHash, String userId, String familyId,
                                 Duration ttl) {
        // ConcurrentHashMap.compute runs the remap atomically under the bin lock and never retries,
        // so the compare-and-swap of the family pointer (and the paired token write) are a single
        // atomic step per family — exactly one of N concurrent identical rotations can win.
        RotationResult[] outcome = {RotationResult.FAMILY_MISSING};
        families.compute(familyId, (key, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(clock.instant())) {
                outcome[0] = RotationResult.FAMILY_MISSING;
                return null;
            }
            if (!constantTimeEquals(existing.value(), expectedCurrentHash)) {
                outcome[0] = RotationResult.REUSE_DETECTED;
                return null; // revoke the family
            }
            outcome[0] = RotationResult.ROTATED;
            Instant expiresAt = clock.instant().plus(ttl);
            tokens.put(newTokenHash, new Expiring<>(new RefreshRecord(userId, familyId), expiresAt));
            return new Expiring<>(newTokenHash, expiresAt);
        });
        return outcome[0];
    }

    @Override
    public void deleteFamily(String familyId) {
        families.remove(familyId);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private <T> Optional<T> live(Expiring<T> entry, Runnable evict) {
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            evict.run();
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }
}
