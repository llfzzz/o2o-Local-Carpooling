package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.domain.OrderSnapshot;
import com.o2o.carpooling.common.domain.OrderStateMachine;
import com.o2o.carpooling.common.domain.OrderStatus;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.domain.TripStatus;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.UUID;

@Service
class OrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderRepository orderRepository;
    private final TripClient tripClient;
    private final OrderStateMachine stateMachine = new OrderStateMachine();
    private final Duration paymentDeadline;

    OrderService(
        OrderRepository orderRepository,
        TripClient tripClient,
        @Value("${orders.payment-deadline:PT15M}") Duration paymentDeadline
    ) {
        this.orderRepository = orderRepository;
        this.tripClient = tripClient;
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
        }
        return get(orderId);
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
        orderRepository.save(new OrderRepository.NewOrder(
            orderId,
            command.tripId(),
            command.riderId(),
            command.idempotencyKey(),
            command.seats(),
            amount,
            now.plus(paymentDeadline),
            now
        ));
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
}
