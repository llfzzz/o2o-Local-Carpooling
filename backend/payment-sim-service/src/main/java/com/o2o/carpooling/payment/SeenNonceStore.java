package com.o2o.carpooling.payment;

import java.time.Duration;

/**
 * Short-lived record of webhook nonces already accepted, used for replay protection. A nonce is
 * only ever registered after its enclosing callback has passed HMAC verification, so an attacker
 * cannot poison the store with forged requests.
 */
interface SeenNonceStore {

    /**
     * Atomically register a nonce with a TTL. Returns {@code true} if it was newly registered
     * (first time seen) and {@code false} if it was already present (a replay).
     */
    boolean registerIfAbsent(String key, Duration ttl);
}
