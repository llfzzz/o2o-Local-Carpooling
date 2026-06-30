package com.o2o.carpooling.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Interactive mock channel for the demo profile. It "delivers" every supported channel by
 * letting {@link NotificationService} persist a Demo Inbox record; the user then retrieves the
 * content from the inbox. Active only when {@code providers.notification.type=demo}.
 */
@Component
@ConditionalOnProperty(prefix = "providers.notification", name = "type", havingValue = "demo")
class DemoNotificationChannelAdapter implements NotificationChannelAdapter {

    @Override
    public boolean supports(ChannelType channel) {
        return true;
    }

    @Override
    public DeliveryStatus send(NotificationMessage message) {
        // Demo: delivery always succeeds; the record itself is the "received" message.
        return DeliveryStatus.DELIVERED;
    }
}
