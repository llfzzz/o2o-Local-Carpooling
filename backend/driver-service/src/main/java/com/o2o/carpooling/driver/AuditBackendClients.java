package com.o2o.carpooling.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "audit-service", contextId = "driverAuditFeignClient")
interface DriverAuditFeignClient {
    @PostMapping("/api/audits/logs")
    void append(@RequestBody AuditAppendRequest request);
}

@Component
class BestEffortAuditClient implements AuditClient {

    private static final Logger log = LoggerFactory.getLogger(BestEffortAuditClient.class);

    private final DriverAuditFeignClient auditFeignClient;

    BestEffortAuditClient(DriverAuditFeignClient auditFeignClient) {
        this.auditFeignClient = auditFeignClient;
    }

    @Override
    public void append(String actorId, String action, String targetType, String targetId, Map<String, String> metadata) {
        try {
            auditFeignClient.append(new AuditAppendRequest(actorId, action, targetType, targetId, metadata));
        } catch (RuntimeException exception) {
            log.warn("Audit append failed action={} targetType={} targetId={}", action, targetType, targetId, exception);
        }
    }
}

record AuditAppendRequest(String actorId, String action, String targetType, String targetId, Map<String, String> metadata) {
}
