package com.o2o.carpooling.trip;

import java.util.Optional;

/**
 * Short-lived storage for live driver positions.
 *
 * <p>Entries expire on their own after the presence TTL, which is what makes "driver went offline"
 * work without a heartbeat-reaper: a driver who stops reporting simply disappears. Nothing here is
 * durable, by design (see {@link DriverLocation}).
 */
interface DriverPresenceStore {

    /** Stores the position, refreshing its TTL. */
    void put(DriverLocation location);

    /** The driver's last position for this trip, or empty when absent or expired. */
    Optional<DriverLocation> find(String tripId);

    /** Ends sharing immediately, rather than waiting for the TTL. */
    void clear(String tripId);
}
