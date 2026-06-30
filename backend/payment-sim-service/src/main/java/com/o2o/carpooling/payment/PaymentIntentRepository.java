package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PaymentIntentStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
class PaymentIntentRepository {

    private final JdbcClient jdbcClient;

    PaymentIntentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(PaymentIntent intent, String idempotencyKey) {
        jdbcClient.sql("""
            insert into payment_intents
              (intent_id, order_id, rider_id, idempotency_key, amount, currency, status, provider, provider_ref, created_at, updated_at)
            values
              (:intentId, :orderId, :riderId, :idempotencyKey, :amount, :currency, :status, :provider, :providerRef, :createdAt, :updatedAt)
            """)
            .param("intentId", intent.intentId())
            .param("orderId", intent.orderId())
            .param("riderId", intent.riderId())
            .param("idempotencyKey", idempotencyKey)
            .param("amount", intent.amount().amount())
            .param("currency", intent.amount().currency())
            .param("status", intent.status().name())
            .param("provider", intent.provider())
            .param("providerRef", intent.providerRef())
            .param("createdAt", Timestamp.from(intent.createdAt()))
            .param("updatedAt", Timestamp.from(intent.updatedAt()))
            .update();
    }

    Optional<PaymentIntent> findByOrderIdAndIdempotencyKey(String orderId, String idempotencyKey) {
        return jdbcClient.sql("""
            select intent_id, order_id, rider_id, amount, currency, status, provider, provider_ref, created_at, updated_at
            from payment_intents where order_id = :orderId and idempotency_key = :idempotencyKey
            """)
            .param("orderId", orderId)
            .param("idempotencyKey", idempotencyKey)
            .query(this::mapRow)
            .optional();
    }

    Optional<PaymentIntent> findByIntentId(String intentId) {
        return jdbcClient.sql("""
            select intent_id, order_id, rider_id, amount, currency, status, provider, provider_ref, created_at, updated_at
            from payment_intents where intent_id = :intentId
            """)
            .param("intentId", intentId)
            .query(this::mapRow)
            .optional();
    }

    /** Optimistic status transition keyed by the expected current status. */
    boolean transition(String intentId, PaymentIntentStatus from, PaymentIntentStatus to, Instant now) {
        return jdbcClient.sql("""
            update payment_intents set status = :to, updated_at = :now
            where intent_id = :intentId and status = :from
            """)
            .param("to", to.name())
            .param("from", from.name())
            .param("now", Timestamp.from(now))
            .param("intentId", intentId)
            .update() > 0;
    }

    /** Record a callback event id for replay/idempotency; returns false if already seen. */
    boolean recordCallbackEvent(String eventId, String intentId, String outcome, Instant now) {
        try {
            jdbcClient.sql("""
                insert into payment_callback_events (event_id, intent_id, outcome, received_at)
                values (:eventId, :intentId, :outcome, :now)
                """)
                .param("eventId", eventId)
                .param("intentId", intentId)
                .param("outcome", outcome)
                .param("now", Timestamp.from(now))
                .update();
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private PaymentIntent mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new PaymentIntent(
            rs.getString("intent_id"),
            rs.getString("order_id"),
            rs.getString("rider_id"),
            new Money(rs.getBigDecimal("amount"), rs.getString("currency")),
            PaymentIntentStatus.valueOf(rs.getString("status")),
            rs.getString("provider"),
            rs.getString("provider_ref"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
