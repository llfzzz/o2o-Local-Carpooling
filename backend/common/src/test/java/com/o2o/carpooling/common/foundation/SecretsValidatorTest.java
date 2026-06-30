package com.o2o.carpooling.common.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretsValidatorTest {

    @Test
    void rejectsPlaceholderSecretUnderStaging() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");
        environment.setProperty("security.jwt.base64-secret", "replace-with-64-byte-base64-hs512-secret");

        assertThatThrownBy(new SecretsValidator(environment)::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("security.jwt.base64-secret");
    }

    @Test
    void acceptsStrongUniqueSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        environment.setProperty("security.jwt.base64-secret", randomBase64(64));

        assertThatCode(new SecretsValidator(environment)::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void ignoresBlankSecrets() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThatCode(new SecretsValidator(environment)::afterPropertiesSet).doesNotThrowAnyException();
    }

    private String randomBase64(int bytes) {
        byte[] material = new byte[bytes];
        new SecureRandom().nextBytes(material);
        return Base64.getEncoder().encodeToString(material);
    }
}
