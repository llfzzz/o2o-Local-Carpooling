package com.o2o.carpooling.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDashboardServiceTest {

    @Test
    void aggregatesDriverAndOrderCountsFromRealBackendClients() {
        AdminDashboardService service = new AdminDashboardService(
            () -> 2,
            () -> new OrderAdminMetrics(5, 3, 1)
        );

        DashboardSummary summary = service.dashboard();

        assertThat(summary.pendingDriverReviews()).isEqualTo(2);
        assertThat(summary.todayOrders()).isEqualTo(5);
        assertThat(summary.lockedOrders()).isEqualTo(3);
        assertThat(summary.overduePendingPayments()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo("live-mvp");
    }
}
