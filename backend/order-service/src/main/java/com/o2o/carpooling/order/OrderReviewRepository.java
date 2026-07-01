package com.o2o.carpooling.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
class OrderReviewRepository {

    private final JdbcClient jdbcClient;

    OrderReviewRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Insert a review. The unique {@code order_id} enforces one-review-per-order at the DB level. */
    void save(OrderReview review) {
        jdbcClient.sql("""
            insert into order_reviews (review_id, order_id, trip_id, reviewer_id, rating, comment, created_at)
            values (:reviewId, :orderId, :tripId, :reviewerId, :rating, :comment, :createdAt)
            """)
            .param("reviewId", review.reviewId())
            .param("orderId", review.orderId())
            .param("tripId", review.tripId())
            .param("reviewerId", review.reviewerId())
            .param("rating", review.rating())
            .param("comment", review.comment())
            .param("createdAt", Timestamp.from(review.createdAt()))
            .update();
    }

    Optional<OrderReview> findByOrderId(String orderId) {
        return jdbcClient.sql("""
            select review_id, order_id, trip_id, reviewer_id, rating, comment, created_at
            from order_reviews where order_id = :orderId
            """)
            .param("orderId", orderId)
            .query(this::mapRow)
            .optional();
    }

    private OrderReview mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new OrderReview(
            rs.getString("review_id"),
            rs.getString("order_id"),
            rs.getString("trip_id"),
            rs.getString("reviewer_id"),
            rs.getInt("rating"),
            rs.getString("comment"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
