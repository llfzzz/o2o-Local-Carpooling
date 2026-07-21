package com.o2o.carpooling.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Demo SMS provider: "delivers" the login code into the challenge-bound {@link DemoLoginCodeStore}
 * so the login page (and only the login page) can peek it via the demo-peek endpoint. It never
 * creates a notification/inbox record and the code is never returned in the send response.
 * Active only when {@code providers.sms.type=demo}, which the shared provider config permits only
 * under the demo profile — so outside demo no plaintext code is ever stored anywhere.
 */
@Component
@ConditionalOnProperty(prefix = "providers.sms", name = "type", havingValue = "demo")
class DemoSmsProvider implements SmsProvider {

    private final DemoLoginCodeStore store;

    DemoSmsProvider(DemoLoginCodeStore store) {
        this.store = store;
    }

    @Override
    public void send(SmsSendCommand command) {
        store.save(command.phone(), command.correlationId(), command.code(), command.ttl());
    }
}
