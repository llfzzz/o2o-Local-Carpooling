package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.domain.OrderStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class OrderRepository {

    private final JdbcClient jdbcClient;

    OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(NewOrder order) {
        jdbcClient.sql("""
            insert into orders (
              order_id, trip_id, rider_id, idempotency_key, seats,
              amount, currency, status, payment_deadline_at,
              created_at, updated_at, version
            ) values (
              :orderId, :tripId, :riderId, :idempotencyKey, :seats,
              :amount, :currency, :status, :paymentDeadlineAt,
              :createdAt, :updatedAt, 0
            )
            """)
            .param("orderId", order.orderId())
            .param("tripId", order.tripId())
            .param("riderId", order.riderId())
            .param("idempotencyKey", order.idempotencyKey())
            .param("seats", order.seats())
            .param("amount", order.amount().amount())
            .param("currency", order.amount().currency())
            .param("status", OrderStatus.PENDING_PAYMENT.name())
            .param("paymentDeadlineAt", order.paymentDeadlineAt())
            .param("createdAt", order.createdAt())
            .param("updatedAt", order.createdAt())
            .update();
    }

    Optional<OrderDetail> findByOrderId(String orderId) {
        return jdbcClient.sql("""
            select order_id, trip_id, rider_id, seats, amount, currency, status, created_at
            from orders
            where order_id = :orderId
            """)
            .param("orderId", orderId)
            .query(this::mapDetail)
            .optional();
    }

    Optional<OrderDetail> findByRiderIdAndIdempotencyKey(String riderId, String idempotencyKey) {
        return jdbcClient.sql("""
            select order_id, trip_id, rider_id, seats, amount, currency, status, created_at
            from orders
            where rider_id = :riderId and idempotency_key = :idempotencyKey
            """)
            .param("riderId", riderId)
            .param("idempotencyKey", idempotencyKey)
            .query(this::mapDetail)
            .optional();
    }

    List<OrderDetail> list(String riderId, OrderStatus status, int limit) {
        // Split on rider scope so each path can use a real index (idx_orders_rider_created for the
        // rider path, idx_orders_created for the admin path) instead of the "(:x is null or col=:x)"
        // OR-pattern that defeats index selection, and always bound the result set so the admin
        // both-null path can never scan+filesort the whole table.
        if (riderId != null) {
            return jdbcClient.sql("""
                select order_id, trip_id, rider_id, seats, amount, currency, status, created_at
                from orders
                where rider_id = :riderId
                  and (:status is null or status = :status)
                order by created_at desc, id desc
                limit :limit
                """)
                .param("riderId", riderId)
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .query(this::mapDetail)
                .list();
        }
        return jdbcClient.sql("""
            select order_id, trip_id, rider_id, seats, amount, currency, status, created_at
            from orders
            where (:status is null or status = :status)
            order by created_at desc, id desc
            limit :limit
            """)
            .param("status", status == null ? null : status.name())
            .param("limit", limit)
            .query(this::mapDetail)
            .list();
    }

    List<OrderDetail> findOverduePendingOrders(Instant now) {
        return jdbcClient.sql("""
            select order_id, trip_id, rider_id, seats, amount, currency, status, created_at
            from orders
            where status = :status and payment_deadline_at <= :now
            order by payment_deadline_at asc, id asc
            """)
            .param("status", OrderStatus.PENDING_PAYMENT.name())
            .param("now", now)
            .query(this::mapDetail)
            .list();
    }

    /**
     * Recently-cancelled orders, for the seat-lock reconciliation backstop. Bounded by the recent
     * window (served by idx_orders_created) and a hard limit; status is a residual filter. cancelled
     * statuses only — these are the orders whose seats should be released.
     */
    List<OrderDetail> findRecentlyCancelled(Instant since, int limit) {
        return jdbcClient.sql("""
            select order_id, trip_id, rider_id, seats, amount, currency, status, created_at
            from orders
            where created_at >= :since
              and status in ('TIMEOUT_CANCELLED', 'USER_CANCELLED', 'DRIVER_CANCELLED', 'OPERATOR_CANCELLED')
            order by created_at desc
            limit :limit
            """)
            .param("since", since)
            .param("limit", limit)
            .query(this::mapDetail)
            .list();
    }

    boolean transition(String orderId, OrderStatus fromStatus, OrderStatus toStatus, Instant now) {
        int updated = jdbcClient.sql("""
            update orders
            set status = :toStatus,
                paid_at = case when :toStatus = 'SEAT_LOCKED' then :now else paid_at end,
                cancelled_at = case when :toStatus in ('TIMEOUT_CANCELLED', 'USER_CANCELLED', 'DRIVER_CANCELLED', 'OPERATOR_CANCELLED') then :now else cancelled_at end,
                updated_at = :now,
                version = version + 1
            where order_id = :orderId and status = :fromStatus
            """)
            .param("toStatus", toStatus.name())
            .param("now", now)
            .param("orderId", orderId)
            .param("fromStatus", fromStatus.name())
            .update();
        return updated == 1;
    }

    OrderAdminMetrics metrics(Instant todayStart, Instant now) {
        long todayOrders = jdbcClient.sql("select count(*) from orders where created_at >= :todayStart")
            .param("todayStart", todayStart)
            .query(Long.class)
            .single();
        long lockedOrders = jdbcClient.sql("select count(*) from orders where status = :status")
            .param("status", OrderStatus.SEAT_LOCKED.name())
            .query(Long.class)
            .single();
        long overduePending = jdbcClient.sql("""
            select count(*)
            from orders
            where status = :status and payment_deadline_at <= :now
            """)
            .param("status", OrderStatus.PENDING_PAYMENT.name())
            .param("now", now)
            .query(Long.class)
            .single();
        return new OrderAdminMetrics(todayOrders, lockedOrders, overduePending);
    }

    private OrderDetail mapDetail(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrderDetail(
            resultSet.getString("order_id"),
            resultSet.getString("trip_id"),
            resultSet.getString("rider_id"),
            resultSet.getInt("seats"),
            new Money(resultSet.getBigDecimal("amount"), resultSet.getString("currency")),
            OrderStatus.valueOf(resultSet.getString("status")),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }

    record NewOrder(
        String orderId,
        String tripId,
        String riderId,
        String idempotencyKey,
        int seats,
        Money amount,
        Instant paymentDeadlineAt,
        Instant createdAt
    ) {
    }
}
