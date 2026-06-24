package com.o2o.carpooling.order;

import java.time.Instant;

record OrderOutboxEvent(
    String eventId,
    String aggregateId,
    String eventType,
    String routingKey,
    String payloadJson,
    int attempts,
    Instant nextAttemptAt
) {
}
