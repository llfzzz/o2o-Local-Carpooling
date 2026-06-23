package com.o2o.carpooling.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
class AdminController {

    @GetMapping("/dashboard")
    Map<String, Object> dashboard() {
        return Map.of(
            "pendingDriverReviews", 0,
            "todayOrders", 0,
            "riskAlerts", 0,
            "status", "mock-ready"
        );
    }
}
