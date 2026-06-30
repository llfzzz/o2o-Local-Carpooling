package com.o2o.carpooling.notification;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class NotificationDeliveryRepository {

    private final JdbcClient jdbcClient;

    NotificationDeliveryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(DeliveryRecord record, String revealablePayload, Instant revealExpiresAt) {
        jdbcClient.sql("""
            insert into notification_deliveries
              (delivery_id, user_id, channel, category, title, masked_preview, revealable_payload,
               reveal_expires_at, status, correlation_id, retry_count, created_at, updated_at)
            values
              (:deliveryId, :userId, :channel, :category, :title, :maskedPreview, :revealablePayload,
               :revealExpiresAt, :status, :correlationId, :retryCount, :createdAt, :updatedAt)
            """)
            .param("deliveryId", record.deliveryId())
            .param("userId", record.userId())
            .param("channel", record.channel().name())
            .param("category", record.category())
            .param("title", record.title())
            .param("maskedPreview", record.maskedPreview())
            .param("revealablePayload", revealablePayload)
            .param("revealExpiresAt", revealExpiresAt == null ? null : Timestamp.from(revealExpiresAt))
            .param("status", record.status().name())
            .param("correlationId", record.correlationId())
            .param("retryCount", record.retryCount())
            .param("createdAt", Timestamp.from(record.createdAt()))
            .param("updatedAt", Timestamp.from(record.updatedAt()))
            .update();
    }

    List<DeliveryRecord> findByUserId(String userId, int limit) {
        return jdbcClient.sql("""
            select delivery_id, user_id, channel, category, title, masked_preview, status,
                   correlation_id, retry_count, created_at, updated_at, read_at
            from notification_deliveries
            where user_id = :userId
            order by created_at desc, id desc
            limit :limit
            """)
            .param("userId", userId)
            .param("limit", limit)
            .query(this::mapRow)
            .list();
    }

    Optional<DeliveryRecord> findByDeliveryIdAndUserId(String deliveryId, String userId) {
        return jdbcClient.sql("""
            select delivery_id, user_id, channel, category, title, masked_preview, status,
                   correlation_id, retry_count, created_at, updated_at, read_at
            from notification_deliveries
            where delivery_id = :deliveryId and user_id = :userId
            """)
            .param("deliveryId", deliveryId)
            .param("userId", userId)
            .query(this::mapRow)
            .optional();
    }

    /** Returns the sensitive payload only if it has not expired; enforces ownership and TTL. */
    Optional<String> findRevealablePayload(String deliveryId, String userId, Instant now) {
        return jdbcClient.sql("""
            select revealable_payload
            from notification_deliveries
            where delivery_id = :deliveryId and user_id = :userId
              and revealable_payload is not null
              and (reveal_expires_at is null or reveal_expires_at > :now)
            """)
            .param("deliveryId", deliveryId)
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .query(String.class)
            .optional();
    }

    int markRead(String deliveryId, String userId, Instant now) {
        return jdbcClient.sql("""
            update notification_deliveries
            set status = 'READ', read_at = :now, updated_at = :now
            where delivery_id = :deliveryId and user_id = :userId and status <> 'READ'
            """)
            .param("deliveryId", deliveryId)
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .update();
    }

    /** Operator demo control: drive a delivery's status by id (not user-scoped). */
    int updateStatusByDeliveryId(String deliveryId, DeliveryStatus status, boolean incrementRetry, Instant now) {
        return jdbcClient.sql("""
            update notification_deliveries
            set status = :status,
                retry_count = retry_count + :retryDelta,
                updated_at = :now
            where delivery_id = :deliveryId
            """)
            .param("status", status.name())
            .param("retryDelta", incrementRetry ? 1 : 0)
            .param("now", Timestamp.from(now))
            .param("deliveryId", deliveryId)
            .update();
    }

    private DeliveryRecord mapRow(ResultSet rs, int rowNumber) throws SQLException {
        Timestamp readAt = rs.getTimestamp("read_at");
        return new DeliveryRecord(
            rs.getString("delivery_id"),
            rs.getString("user_id"),
            ChannelType.valueOf(rs.getString("channel")),
            rs.getString("category"),
            rs.getString("title"),
            rs.getString("masked_preview"),
            DeliveryStatus.valueOf(rs.getString("status")),
            rs.getString("correlation_id"),
            rs.getInt("retry_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            readAt == null ? null : readAt.toInstant()
        );
    }
}
