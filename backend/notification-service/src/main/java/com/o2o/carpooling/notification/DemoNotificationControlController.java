package com.o2o.carpooling.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operator-facing Demo Control for notification deliveries (simulate delivered/failed/retried/
 * read). Demo-profile only; the Gateway additionally requires OPERATOR/ADMIN for /api/demo/control/**.
 */
@RestController
@RequestMapping("/api/demo/control/notification")
class DemoNotificationControlController {

    private final NotificationService notificationService;
    private final DemoEndpoints demoEndpoints;

    DemoNotificationControlController(NotificationService notificationService, DemoEndpoints demoEndpoints) {
        this.notificationService = notificationService;
        this.demoEndpoints = demoEndpoints;
    }

    /** Console listing: recent deliveries across users (masked previews only, never payloads). */
    @GetMapping("/deliveries")
    List<DeliveryRecord> deliveries(@RequestParam(required = false, defaultValue = "20") int limit) {
        demoEndpoints.requireControl();
        return notificationService.listRecentDeliveries(limit);
    }

    @PostMapping("/{deliveryId}/status")
    StatusResponse simulateStatus(
        @RequestHeader(value = "X-User-Id", required = false) String actorId,
        @PathVariable String deliveryId,
        @RequestBody StatusRequest request
    ) {
        demoEndpoints.requireControl();
        notificationService.simulateStatus(actorId, deliveryId, request.status());
        return new StatusResponse(deliveryId, request.status());
    }

    record StatusRequest(DeliveryStatus status) {
    }

    record StatusResponse(String deliveryId, DeliveryStatus status) {
    }
}
