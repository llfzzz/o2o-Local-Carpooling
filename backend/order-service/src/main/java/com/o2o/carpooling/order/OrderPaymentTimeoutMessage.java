package com.o2o.carpooling.order;

import java.time.Instant;

record OrderPaymentTimeoutMessage(String orderId, Instant paymentDeadlineAt) {
}
