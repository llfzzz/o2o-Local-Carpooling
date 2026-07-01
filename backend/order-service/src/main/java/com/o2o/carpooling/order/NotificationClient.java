package com.o2o.carpooling.order;

/** Delivers a user-facing notification (e.g. a review invitation) to the notification service. */
interface NotificationClient {
    void notify(String userId, String category, String title, String body);
}
