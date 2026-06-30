package com.o2o.carpooling.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Single-instance fallback when no Redis is configured (also used in unit tests). */
class InMemorySmsCodeStore implements SmsCodeStore {

    private record Entry(String hash, Instant expiresAt, AtomicLong attempts) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemorySmsCodeStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String phone, String codeHash, Duration ttl) {
        entries.put(phone, new Entry(codeHash, clock.instant().plus(ttl), new AtomicLong()));
    }

    @Override
    public Optional<String> findHash(String phone) {
        Entry entry = entries.get(phone);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(phone);
            return Optional.empty();
        }
        return Optional.of(entry.hash());
    }

    @Override
    public long incrementAttempts(String phone, Duration ttl) {
        Entry entry = entries.get(phone);
        if (entry == null) {
            return Long.MAX_VALUE;
        }
        return entry.attempts().incrementAndGet();
    }

    @Override
    public void clear(String phone) {
        entries.remove(phone);
    }
}
