package com.o2o.carpooling.order;

import com.o2o.carpooling.common.domain.NotificationCategory;

/**
 * Enqueues a user-facing Message Center notice for an order lifecycle event. The implementation
 * writes a transactional outbox row (same transaction as the state change) that a scheduled
 * relay delivers to notification-service — so a notice can never be lost by a transient outage
 * and can never block or roll back the business transition it describes.
 */
interface NotificationClient {
    void notify(String userId, NotificationCategory category, String title, String body, String linkType, String linkId);
}
