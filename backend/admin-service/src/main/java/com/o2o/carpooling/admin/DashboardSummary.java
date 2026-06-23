package com.o2o.carpooling.admin;

record DashboardSummary(
    long pendingDriverReviews,
    long todayOrders,
    long lockedOrders,
    long overduePendingPayments,
    long riskAlerts,
    String status
) {
}
