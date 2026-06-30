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

    void saveToken(String tokenHash, String userId, String familyId, Duration ttl);

    void setFamilyCurrent(String familyId, String tokenHash, Duration ttl);

    Optional<RefreshRecord> findToken(String tokenHash);

    Optional<String> familyCurrent(String familyId);

    void deleteFamily(String familyId);
}
