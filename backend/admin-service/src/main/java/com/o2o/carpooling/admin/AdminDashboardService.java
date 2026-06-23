package com.o2o.carpooling.admin;

import org.springframework.stereotype.Service;

@Service
class AdminDashboardService {

    private final DriverReviewClient driverReviewClient;
    private final OrderAdminClient orderAdminClient;

    AdminDashboardService(DriverReviewClient driverReviewClient, OrderAdminClient orderAdminClient) {
        this.driverReviewClient = driverReviewClient;
        this.orderAdminClient = orderAdminClient;
    }

    DashboardSummary dashboard() {
        OrderAdminMetrics metrics = orderAdminClient.metrics();
        return new DashboardSummary(
            driverReviewClient.pendingReviewCount(),
            metrics.todayOrders(),
            metrics.lockedOrders(),
            metrics.overduePendingPayments(),
            0,
            "live-mvp"
        );
    }
}
