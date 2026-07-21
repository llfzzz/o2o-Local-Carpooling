package com.o2o.carpooling.driver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Calls notification-service directly (service-to-service); not via the Gateway. */
@FeignClient(name = "notification-service", contextId = "driverNotificationFeignClient", url = "${O2O_NOTIFICATION_SERVICE_URL:http://127.0.0.1:8112}")
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
