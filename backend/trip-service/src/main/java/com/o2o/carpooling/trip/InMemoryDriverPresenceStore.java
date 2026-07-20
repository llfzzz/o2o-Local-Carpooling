package com.o2o.carpooling.trip;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-instance presence store, used when Redis is absent (tests, minimal local runs).
 *
 * <p>Mirrors the SMS-code and refresh-token stores: correct for one instance, not shared across
 * them. A multi-instance deployment must have Redis, or riders will see a driver as offline
 * whenever their request lands on a different node than the driver's updates.
 */
class InMemoryDriverPresenceStore implements DriverPresenceStore {

    private final Map<String, DriverLocation> byTripId = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    InMemoryDriverPresenceStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public void put(DriverLocation location) {
        byTripId.put(location.tripId(), location);
    }

    @Override
    public Optional<DriverLocation> find(String tripId) {
        DriverLocation location = byTripId.get(tripId);
        if (location == null) {
            return Optional.empty();
        }
        // Expire lazily so a stale entry is never returned as live.
        if (location.isStale(clock.instant(), ttl)) {
            byTripId.remove(tripId, location);
            return Optional.empty();
        }
        return Optional.of(location);
    }

    @Override
    public void clear(String tripId) {
        byTripId.remove(tripId);
    }
}
