package com.o2o.carpooling.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Relays pending Message Center notices to notification-service with retry (at-least-once).
 * The outbox event_id travels as the receiver's dedupeKey, so a redelivered relay can never
 * create a duplicate inbox row. Same shape as {@link OrderAuditOutboxPublisher}.
 */
@Service
class OrderNotificationOutboxPublisher {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    private static final int BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationOutboxPublisher.class);

    private final OrderNotificationOutboxRepository repository;
    private final NotificationFeignClient notificationFeignClient;

    OrderNotificationOutboxPublisher(OrderNotificationOutboxRepository repository, NotificationFeignClient notificationFeignClient) {
        this.repository = repository;
        this.notificationFeignClient = notificationFeignClient;
    }

    @Scheduled(fixedDelayString = "${orders.notification-outbox.publish-fixed-delay:PT10S}")
    void publishDue() {
        publishDue(Instant.now());
    }

    int publishDue(Instant now) {
        int sent = 0;
        for (OrderNotificationOutboxEntry entry : repository.findSendable(now, BATCH_SIZE)) {
            try {
                notificationFeignClient.notify(new NotificationFeignClient.NotifyRequest(
                    entry.userId(),
                    "IN_APP",
                    entry.category(),
                    entry.title(),
                    entry.body(),
                    null,
                    null,
                    null,
                    entry.linkType(),
                    entry.linkId(),
                    entry.eventId()
                ));
                repository.markSent(entry.eventId(), now);
                sent++;
            } catch (RuntimeException exception) {
                log.warn("Notification outbox delivery failed eventId={} category={} attempts={}",
                    entry.eventId(), entry.category(), entry.attempts(), exception);
                repository.markFailed(entry.eventId(), exception.getMessage(), now.plus(RETRY_DELAY), now);
            }
        }
        return sent;
    }
}
