package com.o2o.carpooling.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Demo-only, short-lived plaintext store for the login-page code peek. It is written exclusively
 * by {@link DemoSmsProvider} (which exists only when {@code providers.sms.type=demo}), so outside
 * the demo profile nothing is ever stored here. A peek must present the challengeId returned by
 * the code request that produced the code — reading codes for arbitrary phones is not possible.
 * Entries are removed on successful login, on verification lockout, and by TTL. This store never
 * creates a notification/inbox record and its contents must never be logged.
 */
interface DemoLoginCodeStore {

    /** Save the plaintext code bound to its login challenge, replacing any prior entry. */
    void save(String phone, String challengeId, String code, Duration ttl);

    /** Return the code only when the challengeId matches the entry for this phone. */
    Optional<PeekedCode> find(String phone, String challengeId);

    /** Remove the entry (successful login, lockout, or re-issue). */
    void clear(String phone);

    record PeekedCode(String code, Instant expiresAt) {
    }
}
