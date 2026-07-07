package com.o2o.carpooling.admin;

import com.o2o.carpooling.common.domain.DriverVerificationStatus;
import com.o2o.carpooling.common.domain.VerificationCase;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "driver-service", contextId = "adminDriverVerificationClient", url = "${O2O_DRIVER_SERVICE_URL:http://127.0.0.1:8103}")
interface DriverVerificationFeignClient {
    @GetMapping("/api/drivers/verification-cases")
    List<VerificationCase> listCases();
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
        return driverVerificationFeignClient.listCases().stream()
            .filter(item -> item.status() == DriverVerificationStatus.OCR_REVIEWABLE)
            .count();
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
