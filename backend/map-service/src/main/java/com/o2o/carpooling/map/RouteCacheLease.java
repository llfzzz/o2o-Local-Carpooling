package com.o2o.carpooling.map;

import java.util.Optional;

/**
 * Cross-instance cache-fill coordination. It exists ONLY to avoid duplicate cache rebuild / provider
 * calls — never for business correctness (seat inventory, orders, payments, identity, authorization).
 * A lost or expired lease at worst causes an extra provider call; it can never produce incorrect data.
 */
interface RouteCacheLease {

    /** Try to become the single filler for a key; returns a unique owner token if acquired. */
    Optional<String> tryAcquire(String cacheKey);

    /** Release the lease iff we still own it (owner-token compare-and-delete; never a blind DEL). */
    void release(String cacheKey, String ownerToken);
}
