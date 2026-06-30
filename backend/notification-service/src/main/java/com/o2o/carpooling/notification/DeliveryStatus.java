package com.o2o.carpooling.notification;

/** Lifecycle of a single delivery record (the demo "inbox" row). */
public enum DeliveryStatus {
    QUEUED,
    DELIVERED,
    FAILED,
    RETRYING,
    READ
}
