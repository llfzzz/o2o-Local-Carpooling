package com.o2o.carpooling.admin;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "driver-service", contextId = "adminDriverVerificationClient", url = "${O2O_DRIVER_SERVICE_URL:http://127.0.0.1:8103}")
interface DriverVerificationFeignClient {
    // Internal (un-gatewayed) count endpoint: one indexed count(*), not the full case list + JSON blobs.
    @GetMapping("/internal/drivers/verification-cases/pending-review-count")
    PendingReviewCount pendingReviewCount();

    record PendingReviewCount(long count) {
    }
}

@FeignClient(name = "order-service", contextId = "adminOrderClient", url = "${O2O_ORDER_SERVICE_URL:http://127.0.0.1:8105}")
interface OrderAdminFeignClient {
    @GetMapping("/api/orders/admin/metrics")
    OrderAdminMetrics metrics();
}

@Component
class FeignDriverReviewClient implements DriverReviewClient {

    private final DriverVerificationFeignClient driverVerificationFeignClient;

    FeignDriverReviewClient(DriverVerificationFeignClient driverVerificationFeignClient) {
        this.driverVerificationFeignClient = driverVerificationFeignClient;
    }

    @Override
    public long pendingReviewCount() {
        return driverVerificationFeignClient.pendingReviewCount().count();
    }
}

@Component
class FeignOrderAdminClient implements OrderAdminClient {

    private final OrderAdminFeignClient orderAdminFeignClient;

    FeignOrderAdminClient(OrderAdminFeignClient orderAdminFeignClient) {
        this.orderAdminFeignClient = orderAdminFeignClient;
    }

    @Override
    public OrderAdminMetrics metrics() {
        return orderAdminFeignClient.metrics();
    }
}
