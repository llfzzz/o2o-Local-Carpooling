package com.o2o.carpooling.trip;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Reads driver capability from driver-service's internal API (not routed through the Gateway),
 * following the same direct-URL pattern as {@code MapFeignClient}.
 */
@FeignClient(name = "driver-service", contextId = "tripDriverCapabilityClient",
    url = "${O2O_DRIVER_SERVICE_URL:http://127.0.0.1:8103}")
interface DriverCapabilityFeignClient {

    @GetMapping("/internal/drivers/{userId}/capability")
    DriverCapabilityClient.DriverCapability capability(@PathVariable("userId") String userId);
}

@Component
class FeignDriverCapabilityClient implements DriverCapabilityClient {

    private final DriverCapabilityFeignClient client;

    FeignDriverCapabilityClient(DriverCapabilityFeignClient client) {
        this.client = client;
    }

    @Override
    public DriverCapability capability(String userId) {
        return client.capability(userId);
    }
}
