package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.domain.OrderSnapshot;
import com.o2o.carpooling.common.domain.OrderStateMachine;
import com.o2o.carpooling.common.domain.OrderStatus;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.domain.TripStatus;
import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class OrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final TripClient tripClient;
    private final AuditClient auditClient;
    private final NotificationClient notificationClient;
    private final OrderStateMachine stateMachine = new OrderStateMachine();
    private final Duration paymentDeadline;

    OrderService(
        OrderRepository orderRepository,
        OrderOutboxRepository outboxRepository,
        TripClient tripClient,
        AuditClient auditClient,
        NotificationClient notificationClient,
        @Value("${orders.payment-deadline:PT15M}") Duration paymentDeadline
    ) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.tripClient = tripClient;
        this.auditClient = auditClient;
        this.notificationClient = notificationClient;
        this.paymentDeadline = paymentDeadline;
    }

    @Transactional
    OrderDetail create(CreateOrderCommand command) {
        validateCreate(command);
        return orderRepository.findByRiderIdAndIdempotencyKey(command.riderId(), command.idempotencyKey())
            .orElseGet(() -> createNewOrder(command));
    }

    OrderDetail get(String orderId) {
        return orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    List<OrderDetail> list(String riderId, OrderStatus status) {
        return orderRepository.list(normalized(riderId), status);
    }

    @Transactional
    OrderDetail markPaid(String orderId) {
        OrderDetail current = get(orderId);
        if (current.status() == OrderStatus.SEAT_LOCKED) {
            return current;
        }
        OrderSnapshot paid = stateMachine.pay(new OrderSnapshot(current.orderId(), current.status()));
        boolean updated = orderRepository.transition(current.orderId(), current.status(), paid.status(), Instant.now());
        if (updated) {
            auditClient.append(current.riderId(), "ORDER_PAID", "ORDER", current.orderId(), Map.of("tripId", current.tripId()));
        }
        return updated ? get(orderId) : get(orderId);
    }

    @Transactional
    OrderDetail timeout(String orderId) {
        OrderDetail current = get(orderId);
        if (current.status() == OrderStatus.TIMEOUT_CANCELLED) {
            return current;
        }
        OrderSnapshot timedOut = stateMachine.timeout(new OrderSnapshot(current.orderId(), current.status()));
        boolean updated = orderRepository.transition(current.orderId(), current.status(), timedOut.status(), Instant.now());
        if (updated) {
            tripClient.releaseSeats(current.tripId(), current.orderId());
            auditClient.append("system-order-service", "ORDER_TIMEOUT", "ORDER", current.orderId(), Map.of("tripId", current.tripId()));
        }
        return get(orderId);
    }

    /**
     * Cancel an active order (PENDING_PAYMENT or paid SEAT_LOCKED) and release its seats. The actor
     * is resolved server-side from the authenticated principal: the order's rider, the trip's
     * driver, or an operator/admin — anyone else is refused. Idempotent for a repeated cancel by
     * the same kind of actor. An optional free-text reason (≤200 chars) is recorded in the audit
     * trail, not on the order row. Refunds for an already-paid order are a real-provider concern,
     * out of scope for the demo.
     */
    @Transactional
    OrderDetail cancel(String orderId, String actorUserId, Set<UserRole> actorRoles, String reason) {
        OrderDetail current = get(orderId);
        String cancelReason = normalizeCancelReason(reason);
        CancelActor actor = resolveCancelActor(current, actorUserId, actorRoles);
        if (current.status() == actor.status()) {
            return current;
        }
        OrderSnapshot cancelled = actor.apply(stateMachine, new OrderSnapshot(current.orderId(), current.status()));
        boolean updated = orderRepository.transition(current.orderId(), current.status(), cancelled.status(), Instant.now());
        if (updated) {
            tripClient.releaseSeats(current.tripId(), current.orderId());
            Map<String, String> metadata = cancelReason == null
                ? Map.of("tripId", current.tripId(), "cancelledBy", actor.name())
                : Map.of("tripId", current.tripId(), "cancelledBy", actor.name(), "reason", cancelReason);
            auditClient.append(actorUserId, actor.auditAction(), "ORDER", current.orderId(), metadata);
        }
        return get(orderId);
    }

    private String normalizeCancelReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORDER_CANCEL_REASON_TOO_LONG",
                "cancellation reason must be at most 200 characters");
        }
        return trimmed;
    }

    /**
     * Mark a paid order as completed (the ride happened). Only the trip's driver or an
     * operator/admin may complete it; the rider cannot self-complete (that would let them fabricate
     * the completion a review depends on). Seats are not released — the trip was consumed.
     */
    @Transactional
    OrderDetail complete(String orderId, String actorUserId, Set<UserRole> actorRoles) {
        OrderDetail current = get(orderId);
        if (current.status() == OrderStatus.COMPLETED) {
            return current;
        }
        authorizeComplete(current, actorUserId, actorRoles);
        OrderSnapshot completed = stateMachine.complete(new OrderSnapshot(current.orderId(), current.status()));
        boolean updated = orderRepository.transition(current.orderId(), current.status(), completed.status(), Instant.now());
        if (updated) {
            auditClient.append(actorUserId, "ORDER_COMPLETED", "ORDER", current.orderId(), Map.of("tripId", current.tripId()));
            // On completion, invite the rider to review (best-effort; never blocks completion).
            notificationClient.notify(current.riderId(), "ORDER_REVIEW_INVITATION", "行程已完成，快去评价",
                "您的行程 " + current.orderId() + " 已完成，欢迎在订单页提交评价。");
        }
        return get(orderId);
    }

    private CancelActor resolveCancelActor(OrderDetail order, String userId, Set<UserRole> roles) {
        if (StringUtils.hasText(userId) && userId.equals(order.riderId())) {
            return CancelActor.USER;
        }
        if (StringUtils.hasText(userId) && isTripDriver(order.tripId(), userId)) {
            return CancelActor.DRIVER;
        }
        if (hasOperatorRole(roles)) {
            return CancelActor.OPERATOR;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "ORDER_CANCEL_FORBIDDEN", "not allowed to cancel this order");
    }

    private void authorizeComplete(OrderDetail order, String userId, Set<UserRole> roles) {
        boolean isDriver = StringUtils.hasText(userId) && isTripDriver(order.tripId(), userId);
        if (!isDriver && !hasOperatorRole(roles)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ORDER_COMPLETE_FORBIDDEN",
                "only the trip driver or an operator can complete this order");
        }
    }

    private boolean isTripDriver(String tripId, String userId) {
        return tripClient.findTrip(tripId).map(TripOffer::driverId).filter(userId::equals).isPresent();
    }

    private boolean hasOperatorRole(Set<UserRole> roles) {
        return roles != null && (roles.contains(UserRole.OPERATOR) || roles.contains(UserRole.ADMIN));
    }

    @Transactional
    OrderDetail expireIfPaymentPending(String orderId) {
        OrderDetail current = get(orderId);
        if (current.status() != OrderStatus.PENDING_PAYMENT) {
            return current;
        }
        return timeout(orderId);
    }

    @Transactional
    int cancelOverduePendingOrders(Instant now) {
        int count = 0;
        for (OrderDetail overdue : orderRepository.findOverduePendingOrders(now)) {
            OrderDetail timedOut = timeout(overdue.orderId());
            if (timedOut.status() == OrderStatus.TIMEOUT_CANCELLED) {
                count++;
            }
        }
        return count;
    }

    OrderAdminMetrics metrics(Instant now) {
        Instant todayStart = LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toInstant();
        return orderRepository.metrics(todayStart, now);
    }

    @Scheduled(fixedDelayString = "${orders.timeout-scan.fixed-delay:PT30S}")
    void cancelOverduePendingOrders() {
        cancelOverduePendingOrders(Instant.now());
    }

    private OrderDetail createNewOrder(CreateOrderCommand command) {
        TripOffer trip = tripClient.findTrip(command.tripId())
            .orElseThrow(() -> new IllegalArgumentException("trip not found: " + command.tripId()));
        if (trip.status() != TripStatus.PUBLISHED) {
            throw new IllegalStateException("trip " + command.tripId() + " is not published");
        }
        if (trip.inventory().availableSeats() < command.seats()) {
            throw new IllegalStateException("not enough seats for trip " + command.tripId());
        }

        String orderId = "order-" + UUID.randomUUID();
        Money amount = new Money(trip.seatPrice().amount().multiply(BigDecimal.valueOf(command.seats())), trip.seatPrice().currency());
        tripClient.lockSeats(command.tripId(), orderId, command.seats());
        Instant now = Instant.now();
        Instant paymentDeadlineAt = now.plus(paymentDeadline);
        orderRepository.save(new OrderRepository.NewOrder(
            orderId,
            command.tripId(),
            command.riderId(),
            command.idempotencyKey(),
            command.seats(),
            amount,
            paymentDeadlineAt,
            now
        ));
        outboxRepository.savePaymentTimeoutRequested(orderId, paymentDeadlineAt, now);
        return get(orderId);
    }

    private void validateCreate(CreateOrderCommand command) {
        if (!StringUtils.hasText(command.tripId())) {
            throw new IllegalArgumentException("tripId is required");
        }
        if (!StringUtils.hasText(command.riderId())) {
            throw new IllegalArgumentException("riderId is required");
        }
        if (command.seats() <= 0) {
            throw new IllegalArgumentException("seats must be positive");
        }
        if (!StringUtils.hasText(command.idempotencyKey())) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** Who initiated a cancellation, and how it maps onto the state machine + audit trail. */
    private enum CancelActor {
        USER(OrderStatus.USER_CANCELLED, "ORDER_CANCELLED_BY_USER"),
        DRIVER(OrderStatus.DRIVER_CANCELLED, "ORDER_CANCELLED_BY_DRIVER"),
        OPERATOR(OrderStatus.OPERATOR_CANCELLED, "ORDER_CANCELLED_BY_OPERATOR");

        private final OrderStatus status;
        private final String auditAction;

        CancelActor(OrderStatus status, String auditAction) {
            this.status = status;
            this.auditAction = auditAction;
        }

        OrderStatus status() {
            return status;
        }

        String auditAction() {
            return auditAction;
        }

        OrderSnapshot apply(OrderStateMachine stateMachine, OrderSnapshot order) {
            return switch (this) {
                case USER -> stateMachine.cancelByUser(order);
                case DRIVER -> stateMachine.cancelByDriver(order);
                case OPERATOR -> stateMachine.cancelByOperator(order);
            };
        }
    }
}
