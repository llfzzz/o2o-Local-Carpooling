package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.NotificationCategory;
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
            notificationClient.notify(current.riderId(), NotificationCategory.ORDER_PAID,
                "支付成功，座位已锁定", "订单支付成功，座位已为您锁定，出发前请留意行程提醒。",
                "ORDER", current.orderId());
            notifyTripDriver(current.tripId(), NotificationCategory.ORDER_PAID,
                "乘客已完成支付", "行程有一笔订座已完成支付（" + current.seats() + " 座）。",
                "TRIP", current.tripId());
        }
        return get(orderId);
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
            notificationClient.notify(current.riderId(), NotificationCategory.ORDER_PAYMENT_TIMEOUT,
                "订单支付超时已取消", "订单未在时限内完成支付，已自动取消并释放座位，您可以重新预订。",
                "ORDER", current.orderId());
            notifyTripDriver(current.tripId(), NotificationCategory.TRIP_SEAT_RELEASED,
                "座位已释放", "一笔订座因支付超时被取消，" + current.seats() + " 个座位已重新可售。",
                "TRIP", current.tripId());
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
            notifyCancellation(current, actor);
        }
        return get(orderId);
    }

    /**
     * Message Center notices for a cancellation: the rider always hears about their order; the
     * driver hears when someone else's action changed their seat inventory. Bodies carry only
     * order/trip ids and seat counts — never the counterparty's identity or the free-text reason.
     */
    private void notifyCancellation(OrderDetail order, CancelActor actor) {
        NotificationCategory category = switch (actor) {
            case USER -> NotificationCategory.ORDER_CANCELLED_BY_USER;
            case DRIVER -> NotificationCategory.ORDER_CANCELLED_BY_DRIVER;
            case OPERATOR -> NotificationCategory.ORDER_CANCELLED_BY_OPERATOR;
        };
        String riderBody = switch (actor) {
            case USER -> "您已取消订单，座位已释放。";
            case DRIVER -> "很抱歉，司机取消了本次行程，座位已释放，您可以重新选择其他行程。";
            case OPERATOR -> "您的订单已被运营取消，座位已释放，如有疑问请联系客服。";
        };
        notificationClient.notify(order.riderId(), category, "订单已取消", riderBody, "ORDER", order.orderId());
        if (actor == CancelActor.USER || actor == CancelActor.OPERATOR) {
            notifyTripDriver(order.tripId(), category, "订座已取消",
                "一笔订座已取消，" + order.seats() + " 个座位已重新可售。", "TRIP", order.tripId());
        }
    }

    /**
     * Driver-facing notice for an order event. The driver id needs a trip lookup; a failed
     * lookup only skips this notice — it must never block or roll back the order transition.
     */
    private void notifyTripDriver(String tripId, NotificationCategory category, String title, String body,
                                  String linkType, String linkId) {
        try {
            tripClient.findTrip(tripId).map(TripOffer::driverId)
                .ifPresent(driverId -> notificationClient.notify(driverId, category, title, body, linkType, linkId));
        } catch (RuntimeException exception) {
            // Best-effort: the rider-side notice and the transition itself are unaffected.
        }
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
            notificationClient.notify(current.riderId(), NotificationCategory.ORDER_COMPLETED,
                "行程已完成", "本次行程已完成，感谢乘坐。", "ORDER", current.orderId());
            notificationClient.notify(current.riderId(), NotificationCategory.ORDER_REVIEW_INVITATION,
                "行程已完成，快去评价", "您的行程已完成，欢迎在订单页提交评价。", "ORDER", current.orderId());
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
        tripClient.lockSeats(command.tripId(), orderId, command.seats(), command.riderId());
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
        notificationClient.notify(command.riderId(), NotificationCategory.ORDER_CREATED,
            "订单已创建，请尽快支付", "座位已为您锁定，请在支付时限内完成支付，超时将自动取消。",
            "ORDER", orderId);
        notificationClient.notify(trip.driverId(), NotificationCategory.TRIP_SEAT_LOCKED,
            "收到新订座", "您的行程有新的订座请求（" + command.seats() + " 座），等待乘客支付。",
            "TRIP", command.tripId());
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
