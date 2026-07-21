package com.o2o.carpooling.common.domain;

/**
 * Message Center categories shared by every notification sender. Senders pass
 * {@code name()} over the internal notify API; notification-service stores the string, so
 * services stay independently deployable while this enum kills cross-service string drift.
 */
public enum NotificationCategory {
    /* rider-facing order lifecycle */
    ORDER_CREATED,
    ORDER_PAID,
    ORDER_PAYMENT_TIMEOUT,
    ORDER_CANCELLED_BY_USER,
    ORDER_CANCELLED_BY_DRIVER,
    ORDER_CANCELLED_BY_OPERATOR,
    ORDER_COMPLETED,
    ORDER_REVIEW_INVITATION,
    /* driver-facing trip lifecycle */
    TRIP_SEAT_LOCKED,
    TRIP_SEAT_RELEASED,
    TRIP_DEPARTURE_REMINDER,
    /* verification results */
    IDENTITY_VERIFICATION_RESULT,
    DRIVER_VERIFICATION_RESULT,
    /* chat + system */
    CHAT_MESSAGE,
    SYSTEM_NOTICE,
    /**
     * Denylisted: login verification codes must never become inbox messages
     * (notification-service rejects this category at notify time).
     */
    AUTH_SMS_CODE
}
