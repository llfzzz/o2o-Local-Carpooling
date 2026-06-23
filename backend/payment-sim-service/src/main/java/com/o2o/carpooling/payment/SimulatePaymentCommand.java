package com.o2o.carpooling.payment;

record SimulatePaymentCommand(String orderId, String idempotencyKey) {
}
