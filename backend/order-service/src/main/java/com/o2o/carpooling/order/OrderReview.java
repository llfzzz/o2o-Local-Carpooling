package com.o2o.carpooling.order;

import java.time.Instant;

/** A rider's review of a completed order. Exactly one review may exist per order. */
public record OrderReview(
    String reviewId,
    String orderId,
    String tripId,
    String reviewerId,
    int rating,
    String comment,
    Instant createdAt
) {
}
