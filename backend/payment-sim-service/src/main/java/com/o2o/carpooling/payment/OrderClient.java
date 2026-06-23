package com.o2o.carpooling.payment;

import com.o2o.carpooling.common.domain.OrderDetail;

import java.util.Optional;

interface OrderClient {
    Optional<OrderDetail> findOrder(String orderId);

    OrderDetail markPaid(String orderId);
}
