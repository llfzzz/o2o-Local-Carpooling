package com.o2o.carpooling.auth;

import java.time.Duration;

/**
 * Provider seam for delivering an SMS. Auth owns code generation and verification; the provider
 * only delivers the message. The demo provider routes it to the notification Demo Inbox; a real
 * provider (Aliyun/Tencent) is selected via {@code providers.sms.type} with no change to the
 * login flow.
 */
interface SmsProvider {

    void send(SmsSendCommand command);

    record SmsSendCommand(String phone, String userId, String code, Duration ttl, String correlationId) {
    }
}
