package com.o2o.carpooling.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/** Calls notification-service directly (service-to-service); not via the Gateway. */
@FeignClient(name = "notification-service", url = "${O2O_NOTIFICATION_SERVICE_URL:http://127.0.0.1:8112}")
interface NotificationFeignClient {

    @PostMapping("/api/notifications")
    void notify(@RequestBody NotifyRequest request);

    @GetMapping("/api/notifications/internal/latest")
    LatestDelivery latest(@RequestParam("userId") String userId, @RequestParam("category") String category);

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

    record LatestDelivery(String deliveryId, String maskedPreview, String value, Instant expiresAt, Instant createdAt) {
    }
}
