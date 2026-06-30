package com.o2o.carpooling.notification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Internal notify API used service-to-service (auth, order, identity, …) via Feign. It is NOT
 * exposed through the Gateway: only /api/demo/inbox/** is routed externally, so end users cannot
 * inject notifications for arbitrary recipients.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    DeliveryReceipt notify(@RequestBody NotifyRequest request) {
        return notificationService.notify(new NotificationMessage(
            request.userId(),
            request.channel(),
            request.category(),
            request.title(),
            request.body(),
            request.revealablePayload(),
            request.revealTtlSeconds() == null ? null : Duration.ofSeconds(request.revealTtlSeconds()),
            request.correlationId()
        ));
    }

    record NotifyRequest(
        String userId,
        ChannelType channel,
        String category,
        String title,
        String body,
        String revealablePayload,
        Long revealTtlSeconds,
        String correlationId
    ) {
    }
}
