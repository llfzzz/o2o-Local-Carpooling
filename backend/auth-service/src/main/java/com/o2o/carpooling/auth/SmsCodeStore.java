package com.o2o.carpooling.auth;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived store for one-time SMS login codes. Only a hash of the code is ever stored; the
 * plaintext code is delivered to the user out of band (the demo notification inbox) and never
 * persisted here.
 */
interface SmsCodeStore {

    /** Save the code hash for a phone with a TTL, resetting any prior attempt counter. */
    void save(String phone, String codeHash, Duration ttl);

    Optional<String> findHash(String phone);

    /** Atomically increment and return the failed-verification attempt count for a phone. */
    long incrementAttempts(String phone, Duration ttl);

    /** Remove the code and attempt counter (single-use consumption or lockout). */
    void clear(String phone);
}
