package com.o2o.carpooling.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-instance fallback when no Redis is configured (also used in unit tests). */
class InMemoryDemoLoginCodeStore implements DemoLoginCodeStore {

    private record Entry(String challengeId, String code, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryDemoLoginCodeStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String phone, String challengeId, String code, Duration ttl) {
        entries.put(phone, new Entry(challengeId, code, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<PeekedCode> find(String phone, String challengeId) {
        Entry entry = entries.get(phone);
        if (entry == null || challengeId == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(phone);
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(entry.challengeId().getBytes(StandardCharsets.UTF_8),
            challengeId.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        return Optional.of(new PeekedCode(entry.code(), entry.expiresAt()));
    }

    @Override
    public void clear(String phone) {
        entries.remove(phone);
    }
}
