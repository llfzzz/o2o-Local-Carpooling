package com.o2o.carpooling.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class OrderAuditOutboxRepository {

    private final JdbcClient jdbcClient;

    OrderAuditOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void enqueue(String auditId, String actorId, String action, String targetType, String targetId, String metadataJson, Instant now) {
        jdbcClient.sql("""
            insert into order_audit_outbox (
              event_id, audit_id, actor_id, action, target_type, target_id,
              metadata_json, status, attempts, next_attempt_at, created_at, updated_at
            ) values (
              :eventId, :auditId, :actorId, :action, :targetType, :targetId,
              :metadataJson, 'PENDING', 0, :now, :now, :now
            )
            """)
            .param("eventId", "evt-" + UUID.randomUUID())
            .param("auditId", auditId)
            .param("actorId", actorId)
            .param("action", action)
            .param("targetType", targetType)
            .param("targetId", targetId)
            .param("metadataJson", metadataJson)
            .param("now", now)
            .update();
    }

    List<OrderAuditOutboxEntry> findSendable(Instant now, int limit) {
        return jdbcClient.sql("""
            select event_id, audit_id, actor_id, action, target_type, target_id, metadata_json, attempts, next_attempt_at
            from order_audit_outbox
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at asc, id asc
            limit :limit
            """)
            .param("now", now)
            .param("limit", limit)
            .query(this::mapRow)
            .list();
    }

    void markSent(String eventId, Instant now) {
        jdbcClient.sql("""
            update order_audit_outbox
            set status = 'SENT', sent_at = :now, updated_at = :now
            where event_id = :eventId
            """)
            .param("eventId", eventId)
            .param("now", now)
            .update();
    }

    void markFailed(String eventId, String lastError, Instant nextAttemptAt, Instant now) {
        jdbcClient.sql("""
            update order_audit_outbox
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

    private OrderAuditOutboxEntry mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrderAuditOutboxEntry(
            resultSet.getString("event_id"),
            resultSet.getString("audit_id"),
            resultSet.getString("actor_id"),
            resultSet.getString("action"),
            resultSet.getString("target_type"),
            resultSet.getString("target_id"),
            resultSet.getString("metadata_json"),
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
