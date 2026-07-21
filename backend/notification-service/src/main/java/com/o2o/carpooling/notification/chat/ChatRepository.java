package com.o2o.carpooling.notification.chat;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class ChatRepository {

    private final JdbcClient jdbcClient;

    ChatRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /* ---- conversations ---- */

    void saveConversation(Conversation conversation) {
        jdbcClient.sql("""
            insert into chat_conversations
              (conversation_id, order_id, trip_id, driver_id, rider_id, origin_text, destination_text,
               status, driver_last_read_id, rider_last_read_id, created_at, updated_at)
            values
              (:conversationId, :orderId, :tripId, :driverId, :riderId, :originText, :destinationText,
               :status, 0, 0, :now, :now)
            """)
            .param("conversationId", conversation.conversationId())
            .param("orderId", conversation.orderId())
            .param("tripId", conversation.tripId())
            .param("driverId", conversation.driverId())
            .param("riderId", conversation.riderId())
            .param("originText", conversation.originText())
            .param("destinationText", conversation.destinationText())
            .param("status", conversation.status())
            .param("now", Timestamp.from(conversation.createdAt()))
            .update();
    }

    Optional<Conversation> findByOrderId(String orderId) {
        return selectConversations("where order_id = :orderId")
            .param("orderId", orderId)
            .query(this::mapConversation)
            .optional();
    }

    Optional<Conversation> findByConversationId(String conversationId) {
        return selectConversations("where conversation_id = :conversationId")
            .param("conversationId", conversationId)
            .query(this::mapConversation)
            .optional();
    }

    /** All conversations the user participates in, most recent activity first. */
    List<Conversation> listForUser(String userId, int limit) {
        return selectConversations("""
            where driver_id = :userId or rider_id = :userId
            order by coalesce(last_message_at, created_at) desc, id desc
            limit :limit
            """)
            .param("userId", userId)
            .param("limit", limit)
            .query(this::mapConversation)
            .list();
    }

    void touchConversation(String conversationId, String preview, Instant now) {
        jdbcClient.sql("""
            update chat_conversations
            set last_message_at = :now, last_message_preview = :preview, updated_at = :now
            where conversation_id = :conversationId
            """)
            .param("now", Timestamp.from(now))
            .param("preview", preview)
            .param("conversationId", conversationId)
            .update();
    }

    /** Advance (never rewind) the participant's read watermark. */
    void advanceReadCursor(String conversationId, boolean isDriver, long messageId, Instant now) {
        String column = isDriver ? "driver_last_read_id" : "rider_last_read_id";
        jdbcClient.sql("""
            update chat_conversations
            set %s = greatest(%s, :messageId), updated_at = :now
            where conversation_id = :conversationId
            """.formatted(column, column))
            .param("messageId", messageId)
            .param("now", Timestamp.from(now))
            .param("conversationId", conversationId)
            .update();
    }

    /* ---- messages ---- */

    void saveMessage(String messageId, String conversationId, String senderId, String body,
                     String clientMsgId, Instant now) {
        jdbcClient.sql("""
            insert into chat_messages (message_id, conversation_id, sender_id, body, client_msg_id, created_at)
            values (:messageId, :conversationId, :senderId, :body, :clientMsgId, :now)
            """)
            .param("messageId", messageId)
            .param("conversationId", conversationId)
            .param("senderId", senderId)
            .param("body", body)
            .param("clientMsgId", clientMsgId)
            .param("now", Timestamp.from(now))
            .update();
    }

    Optional<Message> findMessageByClientMsgId(String conversationId, String senderId, String clientMsgId) {
        return selectMessages("where conversation_id = :conversationId and sender_id = :senderId and client_msg_id = :clientMsgId")
            .param("conversationId", conversationId)
            .param("senderId", senderId)
            .param("clientMsgId", clientMsgId)
            .query(this::mapMessage)
            .optional();
    }

    /**
     * Keyset page of a conversation's messages. Returns the newest {@code limit} messages older
     * than {@code beforeId} (null = latest page), in ascending id order for direct rendering.
     */
    List<Message> listMessages(String conversationId, Long beforeId, int limit) {
        StringBuilder where = new StringBuilder("where conversation_id = :conversationId");
        if (beforeId != null) {
            where.append(" and id < :beforeId");
        }
        var spec = selectMessages(where + " order by id desc limit :limit")
            .param("conversationId", conversationId)
            .param("limit", limit);
        if (beforeId != null) {
            spec = spec.param("beforeId", beforeId);
        }
        List<Message> newestFirst = spec.query(this::mapMessage).list();
        return newestFirst.reversed();
    }

    long latestMessageId(String conversationId) {
        return jdbcClient.sql("select coalesce(max(id), 0) from chat_messages where conversation_id = :conversationId")
            .param("conversationId", conversationId)
            .query(Long.class)
            .single();
    }

    /** Messages from the counterpart newer than the participant's read watermark, per conversation. */
    long countUnreadInConversation(Conversation conversation, String userId) {
        long watermark = conversation.driverId().equals(userId)
            ? conversation.driverLastReadId()
            : conversation.riderLastReadId();
        return jdbcClient.sql("""
            select count(*) from chat_messages
            where conversation_id = :conversationId and sender_id <> :userId and id > :watermark
            """)
            .param("conversationId", conversation.conversationId())
            .param("userId", userId)
            .param("watermark", watermark)
            .query(Long.class)
            .single();
    }

    long countUnreadForUser(String userId) {
        Long total = jdbcClient.sql("""
            select count(*)
            from chat_messages m
            join chat_conversations c on m.conversation_id = c.conversation_id
            where m.sender_id <> :userId
              and ((c.driver_id = :userId and m.id > c.driver_last_read_id)
                or (c.rider_id = :userId and m.id > c.rider_last_read_id))
            """)
            .param("userId", userId)
            .query(Long.class)
            .single();
        return total == null ? 0 : total;
    }

    /* ---- mapping ---- */

    private JdbcClient.StatementSpec selectConversations(String whereClause) {
        return jdbcClient.sql("""
            select conversation_id, order_id, trip_id, driver_id, rider_id, origin_text,
                   destination_text, status, driver_last_read_id, rider_last_read_id,
                   last_message_at, last_message_preview, created_at
            from chat_conversations
            """ + whereClause);
    }

    private JdbcClient.StatementSpec selectMessages(String whereClause) {
        return jdbcClient.sql("""
            select id, message_id, conversation_id, sender_id, body, created_at
            from chat_messages
            """ + whereClause);
    }

    private Conversation mapConversation(ResultSet rs, int rowNumber) throws SQLException {
        Timestamp lastMessageAt = rs.getTimestamp("last_message_at");
        return new Conversation(
            rs.getString("conversation_id"),
            rs.getString("order_id"),
            rs.getString("trip_id"),
            rs.getString("driver_id"),
            rs.getString("rider_id"),
            rs.getString("origin_text"),
            rs.getString("destination_text"),
            rs.getString("status"),
            rs.getLong("driver_last_read_id"),
            rs.getLong("rider_last_read_id"),
            lastMessageAt == null ? null : lastMessageAt.toInstant(),
            rs.getString("last_message_preview"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private Message mapMessage(ResultSet rs, int rowNumber) throws SQLException {
        return new Message(
            rs.getLong("id"),
            rs.getString("message_id"),
            rs.getString("conversation_id"),
            rs.getString("sender_id"),
            rs.getString("body"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    record Conversation(
        String conversationId,
        String orderId,
        String tripId,
        String driverId,
        String riderId,
        String originText,
        String destinationText,
        String status,
        long driverLastReadId,
        long riderLastReadId,
        Instant lastMessageAt,
        String lastMessagePreview,
        Instant createdAt
    ) {
        boolean isParticipant(String userId) {
            return StringUtils.hasText(userId) && (driverId.equals(userId) || riderId.equals(userId));
        }
    }

    record Message(
        long cursor,
        String messageId,
        String conversationId,
        String senderId,
        String body,
        Instant createdAt
    ) {
    }
}
