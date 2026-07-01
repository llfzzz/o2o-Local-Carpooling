package com.o2o.carpooling.identity;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Calls notification-service directly (service-to-service) to deliver results to the Demo inbox; not via the Gateway. */
@FeignClient(name = "notification-service")
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
