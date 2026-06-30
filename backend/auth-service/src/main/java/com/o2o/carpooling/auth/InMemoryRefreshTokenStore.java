package com.o2o.carpooling.auth;

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
    public Optional<String> familyCurrent(String familyId) {
        return live(families.get(familyId), () -> families.remove(familyId));
    }

    @Override
    public void deleteFamily(String familyId) {
        families.remove(familyId);
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
