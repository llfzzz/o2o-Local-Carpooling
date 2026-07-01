package com.o2o.carpooling.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Calls notification-service directly (service-to-service); not via the Gateway. */
@FeignClient(name = "notification-service", contextId = "orderNotificationFeignClient")
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
        String correlationId
    ) {
    }
}

/**
 * Best-effort notifier: a failed delivery (e.g. a transient notification-service outage) must never
 * roll back the authoritative order transition that triggered it. The review invitation is a
 * convenience, not a critical path.
 */
@Component
class FeignNotificationClient implements NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(FeignNotificationClient.class);

    private final NotificationFeignClient notificationFeignClient;

    FeignNotificationClient(NotificationFeignClient notificationFeignClient) {
        this.notificationFeignClient = notificationFeignClient;
    }

    @Override
    public void notify(String userId, String category, String title, String body) {
        try {
            notificationFeignClient.notify(new NotificationFeignClient.NotifyRequest(
                userId, "IN_APP", category, title, body, null, null, null));
        } catch (RuntimeException exception) {
            log.warn("failed to deliver notification category={} to user (best-effort)", category, exception);
        }
    }
}
