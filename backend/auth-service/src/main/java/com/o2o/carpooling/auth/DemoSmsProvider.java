package com.o2o.carpooling.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Demo SMS provider: "delivers" the login code by creating a Demo Inbox record in
 * notification-service. The code is the revealable payload (masked by default, revealed
 * explicitly within its TTL) — it is never returned in the send response. Active only when
 * {@code providers.sms.type=demo}.
 */
@Component
@ConditionalOnProperty(prefix = "providers.sms", name = "type", havingValue = "demo")
class DemoSmsProvider implements SmsProvider {

    private final NotificationFeignClient notification;

    DemoSmsProvider(NotificationFeignClient notification) {
        this.notification = notification;
    }

    @Override
    public void send(SmsSendCommand command) {
        long minutes = Math.max(1, command.ttl().toMinutes());
        String body = "您的登录验证码为 " + command.code() + "，" + minutes + " 分钟内有效，请勿向他人泄露。";
        notification.notify(new NotificationFeignClient.NotifyRequest(
            command.userId(),
            "SMS",
            "AUTH_SMS_CODE",
            "登录验证码",
            body,
            command.code(),
            command.ttl().toSeconds(),
            command.correlationId()
        ));
    }
}
