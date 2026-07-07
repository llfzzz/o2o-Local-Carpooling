package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.domain.OrderStatus;
import com.o2o.carpooling.common.domain.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;
    private final OrderReviewService orderReviewService;

    OrderController(OrderService orderService, OrderReviewService orderReviewService) {
        this.orderService = orderService;
        this.orderReviewService = orderReviewService;
    }

    @PostMapping
    OrderDetail create(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody CreateOrderRequest request
    ) {
        return orderService.create(new CreateOrderCommand(
            request.tripId(),
            resolveRiderId(currentUserId, request.riderId()),
            request.seats(),
            request.idempotencyKey()
        ));
    }

    @GetMapping("/{orderId}")
    OrderDetail get(@PathVariable String orderId) {
        return orderService.get(orderId);
    }

    @GetMapping
    List<OrderDetail> list(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestParam(required = false) String riderId,
        @RequestParam(required = false) OrderStatus status
    ) {
        return orderService.list(resolveOptionalRiderId(currentUserId, riderId), status);
    }

    @PostMapping("/{orderId}/pay")
    OrderDetail pay(@PathVariable String orderId) {
        return orderService.markPaid(orderId);
    }

    @PostMapping("/{orderId}/timeout")
    OrderDetail timeout(@PathVariable String orderId) {
        return orderService.timeout(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    OrderDetail cancel(
        @PathVariable String orderId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = "X-User-Roles", required = false) String currentRoles,
        @RequestBody(required = false) CancelRequest request
    ) {
        return orderService.cancel(orderId, currentUserId, roles(currentRoles), request == null ? null : request.reason());
    }

    @PostMapping("/{orderId}/complete")
    OrderDetail complete(
        @PathVariable String orderId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = "X-User-Roles", required = false) String currentRoles
    ) {
        return orderService.complete(orderId, currentUserId, roles(currentRoles));
    }

    @PostMapping("/{orderId}/review")
    OrderReview submitReview(
        @PathVariable String orderId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody ReviewRequest request
    ) {
        return orderReviewService.submit(orderId, currentUserId, request.rating(), request.comment());
    }

    @GetMapping("/{orderId}/review")
    OrderReview getReview(@PathVariable String orderId) {
        return orderReviewService.get(orderId);
    }

    @GetMapping("/admin")
    List<OrderDetail> adminList(@RequestParam(required = false) OrderStatus status) {
        return orderService.list(null, status);
    }

    @GetMapping("/admin/metrics")
    OrderAdminMetrics adminMetrics() {
        return orderService.metrics(Instant.now());
    }

    private String resolveRiderId(String currentUserId, String fallbackRiderId) {
        String resolved = resolveOptionalRiderId(currentUserId, fallbackRiderId);
        if (!StringUtils.hasText(resolved)) {
            throw new IllegalArgumentException("riderId is required");
        }
        return resolved;
    }

    private String resolveOptionalRiderId(String currentUserId, String fallbackRiderId) {
        return StringUtils.hasText(currentUserId) ? currentUserId : fallbackRiderId;
    }

    /** Parse the Gateway-injected, comma-separated roles header; spoofed inbound values are stripped upstream. */
    private Set<UserRole> roles(String header) {
        if (!StringUtils.hasText(header)) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(UserRole::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Optional cancel body; the reason is free text recorded in the audit trail. */
    record CancelRequest(String reason) {
    }

    record CreateOrderRequest(String tripId, String riderId, int seats, String idempotencyKey) {
    }

    record ReviewRequest(int rating, String comment) {
    }
}
