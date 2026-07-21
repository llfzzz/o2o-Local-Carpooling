package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.OrderDetail;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service order lookup (chat participant resolution in notification-service).
 * Deliberately NOT under /api/**: the Gateway never routes it, so it is unreachable from
 * clients — this is what lets it return the full order (incl. riderId) without an
 * ownership check, unlike the external order endpoints.
 */
@RestController
@RequestMapping("/internal/orders")
class InternalOrderController {

    private final OrderRepository orderRepository;

    InternalOrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/{orderId}")
    OrderDetail get(@PathVariable String orderId) {
        return orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "order not found: " + orderId));
    }
}
