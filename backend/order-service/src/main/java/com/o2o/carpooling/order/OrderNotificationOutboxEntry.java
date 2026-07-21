package com.o2o.carpooling.order;

import java.time.Instant;

record OrderNotificationOutboxEntry(
    String eventId,
    String userId,
    String category,
    String title,
    String body,
    String linkType,
    String linkId,
    int attempts,
    Instant nextAttemptAt
) {
}
