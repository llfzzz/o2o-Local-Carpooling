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
 * Production Message Center for the authenticated user (successor of the demo-gated
 * /api/demo/inbox). JWT-protected at the Gateway and strictly scoped to the injected X-User-Id:
 * a user can only ever see, read, or reveal their own deliveries. Sensitive payloads stay
 * masked in every listing; the value only surfaces through the explicit reveal action, which is
 * owner-scoped, TTL-bound and audited.
 */
@RestController
@RequestMapping("/api/inbox")
class InboxController {

    private static final int DEFAULT_PAGE = 20;

    private final NotificationService notificationService;

    InboxController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    InboxPage list(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int limit,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) String category
    ) {
        List<DeliveryRecord> items = notificationService.listInbox(currentUserId, limit, cursor, category);
        // A full page means there may be more; the client passes the last cursor to continue.
        Long nextCursor = items.size() < Math.min(Math.max(limit, 1), 100)
            ? null
            : items.get(items.size() - 1).cursor();
        return new InboxPage(items, nextCursor);
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount(@RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        return new UnreadCount(notificationService.unreadCount(currentUserId));
    }

    @PostMapping("/{deliveryId}/read")
    ReadResponse markRead(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String deliveryId
    ) {
        return new ReadResponse(deliveryId, notificationService.markRead(currentUserId, deliveryId));
    }

    @PostMapping("/read-all")
    ReadAllResponse markAllRead(@RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        return new ReadAllResponse(notificationService.markAllRead(currentUserId));
    }

    @PostMapping("/{deliveryId}/reveal")
    RevealResponse reveal(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String deliveryId
    ) {
        String value = notificationService.reveal(currentUserId, deliveryId);
        return new RevealResponse(deliveryId, value, Instant.now());
    }

    record InboxPage(List<DeliveryRecord> items, Long nextCursor) {
    }

    record UnreadCount(long unread) {
    }

    record ReadResponse(String deliveryId, boolean updated) {
    }

    record ReadAllResponse(int updated) {
    }

    record RevealResponse(String deliveryId, String value, Instant revealedAt) {
    }
}
