package com.o2o.carpooling.notification;

/**
 * Provider seam for one notification channel. The demo adapter records deliveries into the
 * Demo Inbox; future real adapters (APNs/FCM push, Aliyun/Tencent SMS, an in-app socket) implement
 * the same contract and are selected via {@code providers.notification.type} without touching the
 * core notification flow.
 */
public interface NotificationChannelAdapter {

    boolean supports(ChannelType channel);

    /** Hand the message to the provider; returns the resulting delivery status. */
    DeliveryStatus send(NotificationMessage message);
}
