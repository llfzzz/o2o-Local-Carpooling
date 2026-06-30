package com.o2o.carpooling.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * User-facing Demo Inbox. Demo-profile only and strictly scoped to the authenticated user
 * (Gateway-injected X-User-Id): a user can only ever see and reveal their own deliveries.
 */
@RestController
@RequestMapping("/api/demo/inbox")
class DemoInboxController {

    private final NotificationService notificationService;
    private final DemoEndpoints demoEndpoints;

    DemoInboxController(NotificationService notificationService, DemoEndpoints demoEndpoints) {
        this.notificationService = notificationService;
        this.demoEndpoints = demoEndpoints;
    }

    @GetMapping
    List<DeliveryRecord> list(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        demoEndpoints.requireInbox();
        return notificationService.listInbox(currentUserId, limit);
    }

    @PostMapping("/{deliveryId}/reveal")
    RevealResponse reveal(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String deliveryId
    ) {
        demoEndpoints.requireInbox();
        String value = notificationService.reveal(currentUserId, deliveryId);
        return new RevealResponse(deliveryId, value, Instant.now());
    }

    @PostMapping("/{deliveryId}/read")
    ReadResponse markRead(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String deliveryId
    ) {
        demoEndpoints.requireInbox();
        return new ReadResponse(deliveryId, notificationService.markRead(currentUserId, deliveryId));
    }

    record RevealResponse(String deliveryId, String value, Instant revealedAt) {
    }

    record ReadResponse(String deliveryId, boolean updated) {
    }
}
