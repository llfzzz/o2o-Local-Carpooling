package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.CoordinateTransform;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.domain.TripStatus;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Live driver position for an in-progress trip.
 *
 * <p>Two distinct permissions, deliberately not conflated:
 * <ul>
 *   <li><strong>Reporting</strong> — only the authenticated driver <em>of that trip</em>. Driver
 *       capability was verified once at publish time, so a trip existing under this principal is
 *       itself the proof; no cross-service call is needed on this 10-second hot path.</li>
 *   <li><strong>Watching</strong> — the trip's driver, or a rider holding a LOCKED seat on it.
 *       Nobody else, which is why browsing trips cannot be used to harvest driver positions.</li>
 * </ul>
 *
 * <p>Non-participants get 404, not 403: a 403 would confirm the trip exists and is being tracked.
 */
@Service
class DriverLocationService {

    private final TripRepository tripRepository;
    private final DriverPresenceStore presenceStore;
    private final FixedWindowRateLimiter rateLimiter;
    private final TripMatchingProperties properties;
    private final Clock clock;

    DriverLocationService(
        TripRepository tripRepository,
        DriverPresenceStore presenceStore,
        FixedWindowRateLimiter rateLimiter,
        TripMatchingProperties properties,
        Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.presenceStore = presenceStore;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    Duration presenceTtl() {
        return properties.getTracking().getPresenceTtl();
    }

    /** Records a position reported by the trip's own driver. */
    DriverLocation report(String tripId, String currentUserId, ReportedLocation reported) {
        TripOffer trip = requireTrip(tripId);
        requireTripDriver(trip, currentUserId);
        requireTripTrackable(trip);

        if (!rateLimiter.allow(
            "trip:driver-location:" + tripId,
            properties.getTracking().getMaxUpdates(),
            properties.getTracking().getUpdateWindow())) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "DRIVER_LOCATION_RATE_LIMITED",
                "location updates are too frequent");
        }

        Instant now = clock.instant();
        Instant capturedAt = reported.capturedAt() == null ? now : reported.capturedAt();
        if (!DriverLocationPolicy.isTimestampAcceptable(capturedAt, now, presenceTtl())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DRIVER_LOCATION_STALE",
                "location timestamp is outside the accepted window");
        }

        // Trips are stored in the provider datum, so a WGS-84 browser fix converts here.
        GeoPoint point = CoordinateTransform.toDatum(reported.toGeoPoint(), CoordinateDatum.GCJ02);
        DriverLocation next = new DriverLocation(
            tripId, currentUserId, point, reported.headingDegrees(), reported.speedMetersPerSecond(), capturedAt);

        DriverLocation previous = presenceStore.find(tripId).orElse(null);
        if (DriverLocationPolicy.isImpossibleJump(previous, next)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DRIVER_LOCATION_IMPLAUSIBLE",
                "location update rejected as implausible");
        }

        presenceStore.put(next);
        return next;
    }

    /** Ends sharing for this trip immediately rather than waiting for the TTL to lapse. */
    void stopSharing(String tripId, String currentUserId) {
        TripOffer trip = requireTrip(tripId);
        requireTripDriver(trip, currentUserId);
        presenceStore.clear(tripId);
    }

    /**
     * The current position, for a participant of this trip. Empty means "not sharing or gone
     * stale" — a caller must render that as unknown, never as a last-known point shown as live.
     */
    Optional<DriverLocation> watch(String tripId, String currentUserId) {
        TripOffer trip = requireTrip(tripId);
        requireParticipant(trip, tripId, currentUserId);
        return presenceStore.find(tripId)
            .filter(location -> !location.isStale(clock.instant(), presenceTtl()));
    }

    private TripOffer requireTrip(String tripId) {
        return tripRepository.findByTripId(tripId).orElseThrow(this::notFound);
    }

    private void requireTripDriver(TripOffer trip, String currentUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "authentication is required");
        }
        if (!trip.driverId().equals(currentUserId)) {
            // The caller is authenticated but is not this trip's driver.
            throw new BusinessException(HttpStatus.FORBIDDEN, "TRIP_NOT_DRIVER",
                "only this trip's driver may share its location");
        }
    }

    private void requireTripTrackable(TripOffer trip) {
        if (trip.status() != TripStatus.PUBLISHED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_NOT_TRACKABLE",
                "location sharing is only available for a published trip");
        }
    }

    /**
     * A watcher must be the driver or hold a LOCKED seat. Anyone else is told the resource does
     * not exist, so the endpoint cannot be used to probe which trips are live.
     */
    private void requireParticipant(TripOffer trip, String tripId, String currentUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "authentication is required");
        }
        if (trip.driverId().equals(currentUserId)) {
            return;
        }
        if (!tripRepository.hasActiveSeatLockForRider(tripId, currentUserId)) {
            throw notFound();
        }
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "resource not found");
    }

    /** @param capturedAt when the device took the fix; defaults to now when absent. */
    record ReportedLocation(
        Double lat,
        Double lng,
        CoordinateDatum datum,
        Double headingDegrees,
        Double speedMetersPerSecond,
        Instant capturedAt
    ) {

        GeoPoint toGeoPoint() {
            if (lat == null || lng == null) {
                throw new IllegalArgumentException("lat and lng are required");
            }
            if (datum == null) {
                throw new IllegalArgumentException("datum is required");
            }
            return new GeoPoint(lat, lng, datum);
        }
    }
}
