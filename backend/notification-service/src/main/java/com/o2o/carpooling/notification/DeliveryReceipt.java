package com.o2o.carpooling.notification;

import java.time.Instant;

/** Result returned to the calling service after a notify request. Never carries the payload. */
public record DeliveryReceipt(String deliveryId, ChannelType channel, DeliveryStatus status, Instant createdAt) {
}
