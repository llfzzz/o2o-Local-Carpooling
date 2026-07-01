package com.o2o.carpooling.identity;

import com.o2o.carpooling.common.domain.IdentityVerificationStatus;
import com.o2o.carpooling.common.domain.LivenessCheckStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
class IdentityVerificationRepository {

    private final JdbcClient jdbcClient;

    IdentityVerificationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(IdentityVerification verification, String idempotencyKey) {
        jdbcClient.sql("""
            insert into identity_verifications
              (verification_id, user_id, idempotency_key, status, liveness_status, provider, provider_ref, created_at, updated_at)
            values
              (:verificationId, :userId, :idempotencyKey, :status, :livenessStatus, :provider, :providerRef, :createdAt, :updatedAt)
            """)
            .param("verificationId", verification.verificationId())
            .param("userId", verification.userId())
            .param("idempotencyKey", idempotencyKey)
            .param("status", verification.status().name())
            .param("livenessStatus", verification.livenessStatus().name())
            .param("provider", verification.provider())
            .param("providerRef", verification.providerRef())
            .param("createdAt", Timestamp.from(verification.createdAt()))
            .param("updatedAt", Timestamp.from(verification.updatedAt()))
            .update();
    }

    Optional<IdentityVerification> findByVerificationId(String verificationId) {
        return jdbcClient.sql("""
            select verification_id, user_id, status, liveness_status, provider, provider_ref, created_at, updated_at
            from identity_verifications where verification_id = :verificationId
            """)
            .param("verificationId", verificationId)
            .query(this::mapRow)
            .optional();
    }

    /** Whether the user has at least one APPROVED verification session (used by the driver gate). */
    boolean existsApprovedByUserId(String userId) {
        Long count = jdbcClient.sql("""
            select count(*) from identity_verifications
            where user_id = :userId and status = :status
            """)
            .param("userId", userId)
            .param("status", IdentityVerificationStatus.APPROVED.name())
            .query(Long.class)
            .single();
        return count != null && count > 0;
    }

    Optional<IdentityVerification> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey) {
        return jdbcClient.sql("""
            select verification_id, user_id, status, liveness_status, provider, provider_ref, created_at, updated_at
            from identity_verifications where user_id = :userId and idempotency_key = :idempotencyKey
            """)
            .param("userId", userId)
            .param("idempotencyKey", idempotencyKey)
            .query(this::mapRow)
            .optional();
    }

    /** Optimistic session-status transition keyed by the expected current status. */
    boolean transitionSession(String verificationId, IdentityVerificationStatus from, IdentityVerificationStatus to, Instant now) {
        return jdbcClient.sql("""
            update identity_verifications set status = :to, updated_at = :now
            where verification_id = :verificationId and status = :from
            """)
            .param("to", to.name())
            .param("from", from.name())
            .param("now", Timestamp.from(now))
            .param("verificationId", verificationId)
            .update() > 0;
    }

    /** Optimistic liveness-status transition keyed by the expected current liveness status. */
    boolean transitionLiveness(String verificationId, LivenessCheckStatus from, LivenessCheckStatus to, Instant now) {
        return jdbcClient.sql("""
            update identity_verifications set liveness_status = :to, updated_at = :now
            where verification_id = :verificationId and liveness_status = :from
            """)
            .param("to", to.name())
            .param("from", from.name())
            .param("now", Timestamp.from(now))
            .param("verificationId", verificationId)
            .update() > 0;
    }

    private IdentityVerification mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new IdentityVerification(
            rs.getString("verification_id"),
            rs.getString("user_id"),
            IdentityVerificationStatus.valueOf(rs.getString("status")),
            LivenessCheckStatus.valueOf(rs.getString("liveness_status")),
            rs.getString("provider"),
            rs.getString("provider_ref"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
