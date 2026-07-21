package com.o2o.carpooling.notification.chat;

import com.o2o.carpooling.common.domain.OrderStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Service-to-service lookups used ONLY to resolve chat participants from authoritative order and
 * trip records (direct URLs, not via the Gateway). Client-supplied user ids are never accepted.
 */
@FeignClient(name = "order-service", contextId = "chatOrderFeignClient", url = "${O2O_ORDER_SERVICE_URL:http://127.0.0.1:8105}")
interface OrderFeignClient {

    /** Unrouted internal endpoint: full order detail incl. riderId (never exposed via Gateway). */
    @GetMapping("/internal/orders/{orderId}")
    OrderInfo order(@PathVariable("orderId") String orderId);

    record OrderInfo(String orderId, String tripId, String riderId, int seats, OrderStatus status) {
    }
}

@FeignClient(name = "trip-service", contextId = "chatTripFeignClient", url = "${O2O_TRIP_SERVICE_URL:http://127.0.0.1:8104}")
interface TripFeignClient {

    @GetMapping("/api/trips/{tripId}")
    TripInfo trip(@PathVariable("tripId") String tripId);

    record TripInfo(String tripId, String driverId, String originText, String destinationText) {
    }
}
