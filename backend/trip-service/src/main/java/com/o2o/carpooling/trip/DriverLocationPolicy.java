package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.GeoPoint;

import java.time.Duration;
import java.time.Instant;

/**
 * Plausibility rules for incoming driver positions.
 *
 * <p>The client is not trusted: a position is just a number a browser sent us. These checks reject
 * the obviously impossible — a jump no vehicle could make, or a timestamp from the future or the
 * distant past — so a spoofed or replayed update cannot move a driver across the city.
 *
 * <p>This is plausibility, not proof. Defeating it only requires a spoofer that moves slowly, so it
 * is a speed bump rather than a guarantee, and is documented as such in the threat model.
 */
final class DriverLocationPolicy {

    /** ~200 km/h. Above this the update is not a car on a road. */
    static final double MAX_SPEED_METERS_PER_SECOND = 55.0;

    /** Below this gap, GPS jitter dominates and speed is meaningless. */
    private static final Duration MIN_INTERVAL_FOR_SPEED_CHECK = Duration.ofSeconds(2);

    /** Clock skew we tolerate on a client-supplied timestamp. */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(30);

    private DriverLocationPolicy() {
    }

    /** True when {@code next} could not physically follow {@code previous}. */
    static boolean isImpossibleJump(DriverLocation previous, DriverLocation next) {
        if (previous == null) {
            return false;
        }
        Duration elapsed = Duration.between(previous.capturedAt(), next.capturedAt());
        if (elapsed.isNegative() || elapsed.isZero()) {
            // Out-of-order or duplicate timestamps: treat any movement as implausible.
            return distanceMeters(previous.point(), next.point()) > 0;
        }
        if (elapsed.compareTo(MIN_INTERVAL_FOR_SPEED_CHECK) < 0) {
            return false;
        }
        double impliedSpeed = distanceMeters(previous.point(), next.point()) / elapsed.getSeconds();
        return impliedSpeed > MAX_SPEED_METERS_PER_SECOND;
    }

    /** Rejects timestamps from the future or older than the presence TTL. */
    static boolean isTimestampAcceptable(Instant capturedAt, Instant now, Duration ttl) {
        if (capturedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            return false;
        }
        return !capturedAt.plus(ttl).isBefore(now);
    }

    private static double distanceMeters(GeoPoint a, GeoPoint b) {
        return a.distanceMetersTo(b);
    }
}
