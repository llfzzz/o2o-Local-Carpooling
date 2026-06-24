package com.o2o.carpooling.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class OrderOutboxRepository {

    static final String PAYMENT_TIMEOUT_EVENT = "ORDER_PAYMENT_TIMEOUT_REQUESTED";

    private final JdbcClient jdbcClient;

    OrderOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void savePaymentTimeoutRequested(String orderId, Instant paymentDeadlineAt, Instant now) {
        jdbcClient.sql("""
            insert into order_outbox_events (
              event_id, aggregate_type, aggregate_id, event_type, routing_key,
              payload_json, status, attempts, next_attempt_at, created_at, updated_at
            ) values (
              :eventId, 'ORDER', :orderId, :eventType, :routingKey,
              :payloadJson, 'PENDING', 0, :nextAttemptAt, :now, :now
            )
            """)
            .param("eventId", "evt-" + UUID.randomUUID())
            .param("orderId", orderId)
            .param("eventType", PAYMENT_TIMEOUT_EVENT)
            .param("routingKey", "order.payment.timeout.requested")
            .param("payloadJson", payload(orderId, paymentDeadlineAt))
            .param("nextAttemptAt", now)
            .param("now", now)
            .update();
    }

    List<OrderOutboxEvent> findPublishable(Instant now, int limit) {
        return jdbcClient.sql("""
            select event_id, aggregate_id, event_type, routing_key, payload_json, attempts, next_attempt_at
            from order_outbox_events
            where status = 'PENDING' and next_attempt_at <= :now
            order by id asc
            limit :limit
            """)
            .param("now", now)
            .param("limit", limit)
            .query(this::mapRow)
            .list();
    }

    void markPublished(String eventId, Instant now) {
        jdbcClient.sql("""
            update order_outbox_events
            set status = 'PUBLISHED',
                updated_at = :now,
                published_at = :now
            where event_id = :eventId
            """)
            .param("eventId", eventId)
            .param("now", now)
            .update();
    }

    void markFailed(String eventId, String lastError, Instant nextAttemptAt, Instant now) {
        jdbcClient.sql("""
            update order_outbox_events
            set attempts = attempts + 1,
                next_attempt_at = :nextAttemptAt,
                last_error = :lastError,
                updated_at = :now
            where event_id = :eventId and status = 'PENDING'
            """)
            .param("eventId", eventId)
            .param("lastError", truncate(lastError))
            .param("nextAttemptAt", nextAttemptAt)
            .param("now", now)
            .update();
    }

    private OrderOutboxEvent mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrderOutboxEvent(
            resultSet.getString("event_id"),
            resultSet.getString("aggregate_id"),
            resultSet.getString("event_type"),
            resultSet.getString("routing_key"),
            resultSet.getString("payload_json"),
            resultSet.getInt("attempts"),
            resultSet.getTimestamp("next_attempt_at").toInstant()
        );
    }

    private String payload(String orderId, Instant paymentDeadlineAt) {
        return """
            {"orderId":"%s","paymentDeadlineAt":"%s"}
            """.formatted(orderId, paymentDeadlineAt).trim();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
