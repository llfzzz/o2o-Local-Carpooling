package com.o2o.carpooling.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class OrderNotificationOutboxRepository {

    private final JdbcClient jdbcClient;

    OrderNotificationOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void enqueue(String userId, String category, String title, String body, String linkType, String linkId, Instant now) {
        jdbcClient.sql("""
            insert into order_notification_outbox (
              event_id, user_id, category, title, body, link_type, link_id,
              status, attempts, next_attempt_at, created_at, updated_at
            ) values (
              :eventId, :userId, :category, :title, :body, :linkType, :linkId,
              'PENDING', 0, :now, :now, :now
            )
            """)
            .param("eventId", "ntf-evt-" + UUID.randomUUID())
            .param("userId", userId)
            .param("category", category)
            .param("title", title)
            .param("body", body)
            .param("linkType", linkType)
            .param("linkId", linkId)
            .param("now", now)
            .update();
    }

    List<OrderNotificationOutboxEntry> findSendable(Instant now, int limit) {
        return jdbcClient.sql("""
            select event_id, user_id, category, title, body, link_type, link_id, attempts, next_attempt_at
            from order_notification_outbox
            where status = 'PENDING' and next_attempt_at <= :now
            order by id asc
            limit :limit
            """)
            .param("now", now)
            .param("limit", limit)
            .query(this::mapRow)
            .list();
    }

    void markSent(String eventId, Instant now) {
        jdbcClient.sql("""
            update order_notification_outbox
            set status = 'SENT', sent_at = :now, updated_at = :now
            where event_id = :eventId
            """)
            .param("eventId", eventId)
            .param("now", now)
            .update();
    }

    void markFailed(String eventId, String lastError, Instant nextAttemptAt, Instant now) {
        jdbcClient.sql("""
            update order_notification_outbox
            set attempts = attempts + 1,
                next_attempt_at = :nextAttemptAt,
                last_error = :lastError,
                updated_at = :now
            where event_id = :eventId and status = 'PENDING'
            """)
            .param("eventId", eventId)
            .param("nextAttemptAt", nextAttemptAt)
            .param("lastError", truncate(lastError))
            .param("now", now)
            .update();
    }

    private OrderNotificationOutboxEntry mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrderNotificationOutboxEntry(
            resultSet.getString("event_id"),
            resultSet.getString("user_id"),
            resultSet.getString("category"),
            resultSet.getString("title"),
            resultSet.getString("body"),
            resultSet.getString("link_type"),
            resultSet.getString("link_id"),
            resultSet.getInt("attempts"),
            resultSet.getTimestamp("next_attempt_at").toInstant()
        );
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
