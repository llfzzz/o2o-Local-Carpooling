package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.NotificationCategory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Clock;

/** Calls notification-service directly (service-to-service); not via the Gateway. */
@FeignClient(name = "notification-service", contextId = "orderNotificationFeignClient", url = "${O2O_NOTIFICATION_SERVICE_URL:http://127.0.0.1:8112}")
interface NotificationFeignClient {
    @PostMapping("/api/notifications")
    void notify(@RequestBody NotifyRequest request);

    record NotifyRequest(
        String userId,
        String channel,
        String category,
        String title,
        String body,
        String revealablePayload,
        Long revealTtlSeconds,
        String correlationId,
        String linkType,
        String linkId,
        String dedupeKey
    ) {
    }
}

/**
 * Outbox-backed notifier: the notice is committed with the order transition and relayed by
 * {@link OrderNotificationOutboxPublisher}. Durable (survives notification-service outages) and
 * non-blocking (a plain local insert on the business transaction).
 */
@Component
class OutboxNotificationClient implements NotificationClient {

    private final OrderNotificationOutboxRepository outboxRepository;
    private final Clock clock;

    OutboxNotificationClient(OrderNotificationOutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Override
    public void notify(String userId, NotificationCategory category, String title, String body, String linkType, String linkId) {
        outboxRepository.enqueue(userId, category.name(), title, body, linkType, linkId, clock.instant());
    }
}
