package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.NotificationCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Departure reminders: shortly before departure_at, notify the driver and every LOCKED-seat
 * rider once per trip. The trip row's marker keeps the scan idempotent; the receiver-side
 * dedupe key {@code tripId:userId:DEPARTURE} additionally absorbs partial-failure retries —
 * if any send fails, the trip stays unmarked and the whole batch is retried next pass without
 * double-delivering the sends that already landed.
 */
@Service
class TripDepartureReminderService {

    private static final Logger log = LoggerFactory.getLogger(TripDepartureReminderService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final TripRepository tripRepository;
    private final NotificationFeignClient notificationClient;
    private final TripMatchingProperties properties;
    private final Clock clock;

    TripDepartureReminderService(
        TripRepository tripRepository,
        NotificationFeignClient notificationClient,
        TripMatchingProperties properties,
        Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.notificationClient = notificationClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${trip.reminder.scan-fixed-delay:PT60S}")
    void remindDue() {
        remindDue(clock.instant());
    }

    int remindDue(Instant now) {
        var reminder = properties.getReminder();
        List<TripRepository.DepartureReminderTrip> due =
            tripRepository.findTripsDueForDepartureReminder(now, now.plus(reminder.getLead()), reminder.getBatchSize());
        int reminded = 0;
        for (TripRepository.DepartureReminderTrip trip : due) {
            if (remindTrip(trip, now)) {
                reminded++;
            }
        }
        return reminded;
    }

    private boolean remindTrip(TripRepository.DepartureReminderTrip trip, Instant now) {
        String when = TIME.format(trip.departureAt());
        try {
            notify(trip.driverId(), trip.tripId(),
                "行程即将出发", "您发布的行程（" + trip.originText() + " → " + trip.destinationText()
                    + "）将于 " + when + " 出发，请准时到达上车点。");
            for (String riderId : tripRepository.listActiveSeatLockRiders(trip.tripId())) {
                notify(riderId, trip.tripId(),
                    "行程即将出发", "您预订的行程（" + trip.originText() + " → " + trip.destinationText()
                        + "）将于 " + when + " 出发，请提前到达上车点。");
            }
        } catch (RuntimeException exception) {
            // Leave the trip unmarked: the next scan retries it; dedupe keys absorb re-sends.
            log.warn("departure reminder delivery failed tripId={} (will retry)", trip.tripId(), exception);
            return false;
        }
        return tripRepository.markDepartureReminderSent(trip.tripId(), now);
    }

    private void notify(String userId, String tripId, String title, String body) {
        notificationClient.notify(new NotificationFeignClient.NotifyRequest(
            userId, "IN_APP", NotificationCategory.TRIP_DEPARTURE_REMINDER.name(), title, body,
            null, null, null, "TRIP", tripId, tripId + ":" + userId + ":DEPARTURE"));
    }
}
