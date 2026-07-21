package com.o2o.carpooling.auth;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo SMS provider must deliver the login code ONLY into the challenge-bound login-code
 * store — never into notification-service, so a code can never become an inbox message.
 */
class DemoSmsProviderTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryDemoLoginCodeStore store = new InMemoryDemoLoginCodeStore(clock);
    private final DemoSmsProvider provider = new DemoSmsProvider(store);

    @Test
    void deliversTheCodeIntoTheChallengeBoundStoreAndNowhereElse() {
        provider.send(new SmsProvider.SmsSendCommand("13800000000", "user-13800000000",
            "654321", Duration.ofMinutes(5), "chg-abc"));

        assertThat(store.find("13800000000", "chg-abc"))
            .hasValueSatisfying(peeked -> assertThat(peeked.code()).isEqualTo("654321"));
        // A wrong challenge (or another phone) reveals nothing.
        assertThat(store.find("13800000000", "chg-other")).isEmpty();
        assertThat(store.find("13900000000", "chg-abc")).isEmpty();
    }

    @Test
    void hasNoNotificationCollaboratorAtAll() {
        // Structural guard: the provider's only field is the login-code store. If a future edit
        // reintroduces a notification client, this fails — a code must never reach the inbox.
        assertThat(Arrays.stream(DemoSmsProvider.class.getDeclaredFields())
            .map(Field::getType)
            .map(Class::getSimpleName))
            .containsExactly("DemoLoginCodeStore");
    }
}
