package com.o2o.carpooling.order;

record CreateOrderCommand(String tripId, String riderId, int seats, String idempotencyKey) {
}
