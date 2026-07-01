package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.domain.OrderStatus;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Submitting and reading order reviews. Eligibility is enforced server-side: only the order's rider
 * may review, only after the order is COMPLETED, exactly once (DB-unique per order), with a bounded
 * rating and comment. Every submission is audited.
 */
@Service
class OrderReviewService {

    private static final int MAX_COMMENT_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final OrderReviewRepository reviewRepository;
    private final AuditClient auditClient;

    OrderReviewService(OrderRepository orderRepository, OrderReviewRepository reviewRepository, AuditClient auditClient) {
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
        this.auditClient = auditClient;
    }

    @Transactional
    OrderReview submit(String orderId, String reviewerId, int rating, String comment) {
        OrderDetail order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "order not found: " + orderId));
        if (order.status() != OrderStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "REVIEW_ORDER_NOT_COMPLETED",
                "only a completed order can be reviewed");
        }
        if (!StringUtils.hasText(reviewerId) || !reviewerId.equals(order.riderId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "REVIEW_FORBIDDEN", "only the order's rider can review it");
        }
        if (rating < 1 || rating > 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REVIEW_RATING_INVALID", "rating must be between 1 and 5");
        }
        String trimmed = comment == null ? null : comment.trim();
        if (trimmed != null && trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REVIEW_COMMENT_TOO_LONG",
                "comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        // Fast, clean duplicate path; the unique constraint below is the authoritative backstop.
        if (reviewRepository.findByOrderId(orderId).isPresent()) {
            throw alreadySubmitted();
        }
        OrderReview review = new OrderReview(
            "review-" + UUID.randomUUID(), orderId, order.tripId(), reviewerId, rating, trimmed, Instant.now());
        try {
            reviewRepository.save(review);
        } catch (DuplicateKeyException duplicate) {
            throw alreadySubmitted();
        }
        auditClient.append(reviewerId, "ORDER_REVIEW_SUBMITTED", "ORDER", orderId,
            Map.of("tripId", order.tripId(), "rating", String.valueOf(rating)));
        return review;
    }

    OrderReview get(String orderId) {
        return reviewRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "no review for order: " + orderId));
    }

    private BusinessException alreadySubmitted() {
        return new BusinessException(HttpStatus.CONFLICT, "REVIEW_ALREADY_SUBMITTED", "this order has already been reviewed");
    }
}
