package com.o2o.carpooling.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/admin")
class AdminController {

    private final AdminDashboardService adminDashboardService;

    AdminController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/dashboard")
    DashboardSummary dashboard() {
        return adminDashboardService.dashboard();
    }
}
