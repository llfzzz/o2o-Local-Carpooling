package com.o2o.carpooling.auth;

import java.time.Duration;
import java.util.Optional;

/**
 * Storage for refresh-token rotation with reuse detection. Tokens are stored only as hashes,
 * grouped into a "family" (one login session). Rotation advances the family's current token but
 * keeps prior hashes resolvable so a replayed (rotated-away) token is detectable as reuse.
 */
interface RefreshTokenStore {

    record RefreshRecord(String userId, String familyId) {
    }

    /** Outcome of an atomic {@link #rotate} attempt. */
    enum RotationResult {
        /** The presented token was the family's current token; it has been advanced. */
        ROTATED,
        /** A rotated-away token was replayed; the family has been revoked. */
        REUSE_DETECTED,
        /** The family no longer exists (expired or already revoked). */
        FAMILY_MISSING
    }

    void saveToken(String tokenHash, String userId, String familyId, Duration ttl);

    void setFamilyCurrent(String familyId, String tokenHash, Duration ttl);

    Optional<RefreshRecord> findToken(String tokenHash);

    /**
     * Atomically rotate a session family in a single step, so two concurrent refreshes of the same
     * token can never both succeed (the previous get-compare-set spanned three round-trips and
     * raced). If the family's current token hash equals {@code expectedCurrentHash}, advance it to
     * {@code newTokenHash}, persist the new token record, and return {@link RotationResult#ROTATED}.
     * If the current hash differs (a rotated-away token was replayed) revoke the family and return
     * {@link RotationResult#REUSE_DETECTED}. If the family is absent return
     * {@link RotationResult#FAMILY_MISSING}.
     */
    RotationResult rotate(String expectedCurrentHash, String newTokenHash, String userId, String familyId,
                          Duration ttl);

    void deleteFamily(String familyId);
}
