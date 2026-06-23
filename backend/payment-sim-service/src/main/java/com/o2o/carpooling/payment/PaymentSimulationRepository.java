package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PaymentSimulation;
import com.o2o.carpooling.common.domain.PaymentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
class PaymentSimulationRepository {

    private final JdbcClient jdbcClient;

    PaymentSimulationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(PaymentSimulation payment, String idempotencyKey) {
        jdbcClient.sql("""
            insert into payment_simulations (
              payment_id, order_id, idempotency_key, amount, currency, status, paid_at, created_at
            ) values (
              :paymentId, :orderId, :idempotencyKey, :amount, :currency, :status, :paidAt, :createdAt
            )
            """)
            .param("paymentId", payment.paymentId())
            .param("orderId", payment.orderId())
            .param("idempotencyKey", idempotencyKey)
            .param("amount", payment.amount().amount())
            .param("currency", payment.amount().currency())
            .param("status", payment.status().name())
            .param("paidAt", payment.paidAt())
            .param("createdAt", payment.paidAt())
            .update();
    }

    Optional<PaymentSimulation> findByOrderIdAndIdempotencyKey(String orderId, String idempotencyKey) {
        return jdbcClient.sql("""
            select payment_id, order_id, amount, currency, status, paid_at
            from payment_simulations
            where order_id = :orderId and idempotency_key = :idempotencyKey
            """)
            .param("orderId", orderId)
            .param("idempotencyKey", idempotencyKey)
            .query(this::mapRow)
            .optional();
    }

    private PaymentSimulation mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PaymentSimulation(
            resultSet.getString("payment_id"),
            resultSet.getString("order_id"),
            new Money(resultSet.getBigDecimal("amount"), resultSet.getString("currency")),
            PaymentStatus.valueOf(resultSet.getString("status")),
            resultSet.getTimestamp("paid_at").toInstant()
        );
    }
}
