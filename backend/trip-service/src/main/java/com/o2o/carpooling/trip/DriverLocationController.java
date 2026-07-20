package com.o2o.carpooling.trip;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Live pickup tracking.
 *
 * <p>Reporting is driver-only; watching is limited to that trip's participants. Both permissions
 * are enforced in {@link DriverLocationService} against the Gateway-verified principal — a client
 * cannot name the driver, the rider, or the trip it belongs to.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/driver-location")
class DriverLocationController {

    private final DriverLocationService service;
    private final TripMatchingProperties properties;
    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2, Thread.ofVirtual().name("driver-location-sse-", 0).factory());

    DriverLocationController(DriverLocationService service, TripMatchingProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /** Driver reports a position. Called roughly every 10s while sharing is on. */
    @PostMapping
    Map<String, Object> report(
        @PathVariable String tripId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody DriverLocationService.ReportedLocation body
    ) {
        DriverLocation stored = service.report(tripId, currentUserId, body);
        // Deliberately does not echo the coordinates back: the driver already knows where they are,
        // and keeping them out of the response keeps them out of proxy and access logs.
        return Map.of("tripId", stored.tripId(), "acceptedAt", stored.capturedAt().toString());
    }

    /** Driver ends sharing without waiting for the TTL to lapse. */
    @DeleteMapping
    Map<String, Object> stopSharing(
        @PathVariable String tripId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId
    ) {
        service.stopSharing(tripId, currentUserId);
        return Map.of("tripId", tripId, "sharing", false);
    }

    /** One-shot read, for clients that would rather poll than hold a stream open. */
    @GetMapping
    Map<String, Object> current(
        @PathVariable String tripId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId
    ) {
        return describe(service.watch(tripId, currentUserId));
    }

    /**
     * Server-sent stream of the driver's position.
     *
     * <p>One-directional and plain HTTP, so it traverses the existing nginx/Gateway path without
     * new infrastructure. Authorization is checked once up-front and the emitter is bounded by
     * {@code streamMaxDuration}, so a forgotten tab cannot hold a connection open forever.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
        @PathVariable String tripId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId
    ) {
        // Authorize before opening the stream, so an unauthorized caller gets a plain error
        // response rather than an SSE connection that never yields anything.
        service.watch(tripId, currentUserId);

        SseEmitter emitter = new SseEmitter(properties.getTracking().getStreamMaxDuration().toMillis());
        long intervalMillis = properties.getTracking().getStreamInterval().toMillis();

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("driver-location")
                    .data(describe(service.watch(tripId, currentUserId))));
            } catch (IOException | IllegalStateException exception) {
                // Client went away, or the emitter is already complete.
                emitter.complete();
            } catch (RuntimeException exception) {
                // Authorization revoked mid-stream (e.g. the seat lock was released).
                emitter.completeWithError(exception);
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);

        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> {
            task.cancel(true);
            emitter.complete();
        });
        emitter.onError(error -> task.cancel(true));
        return emitter;
    }

    /**
     * Absent or stale reads as {@code sharing: false} with no coordinates — a client must render
     * that as unknown. A last-known position is never dressed up as live.
     */
    private Map<String, Object> describe(Optional<DriverLocation> location) {
        if (location.isEmpty()) {
            return Map.of("sharing", false, "observedAt", Instant.now().toString());
        }
        DriverLocation current = location.get();
        return Map.of(
            "sharing", true,
            "lat", current.point().latitude(),
            "lng", current.point().longitude(),
            "datum", current.point().datum().name(),
            "capturedAt", current.capturedAt().toString(),
            "ageSeconds", current.ageAt(Instant.now()).toSeconds()
        );
    }
}
