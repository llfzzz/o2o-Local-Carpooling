package com.o2o.carpooling.common.foundation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Fail-closed secret check, active only under the {@code staging} and {@code production}
 * profiles. Refuses to start the context if any sensitive property is set to a known demo
 * value or an obvious placeholder. "Missing" secrets are handled fail-closed by their
 * consumers (e.g. {@link JwtTokenService} and the field-encryption cipher reject blank keys).
 *
 * <p>Known weak values are stored as SHA-256 hashes, never as literals, so no usable demo
 * secret lives in the repository. Error messages reference only the property key — never the
 * value — to avoid leaking secrets into logs.
 */
public class SecretsValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SecretsValidator.class);

    /** SHA-256 hashes of demo/sample secrets that must never be used in staging/production. */
    private static final Set<String> BANNED_SECRET_SHA256 = Set.of(
        // historical demo HS512 JWT secret
        "d8975f8aacf2c2b05f5a14c20b05824c8eadebd849b673800b8a5062b24bd390",
        // historical demo AES-256 field-encryption key
        "2cf5e6ec387461b4bf954f587ad4d957753fcbc48bf892b5e49996b90cf3b476"
    );

    private static final List<String> SECRET_KEYS = List.of(
        "security.jwt.base64-secret",
        "security.field-encryption-key-base64",
        "providers.payment.webhook-secret"
    );

    private final Environment environment;

    public SecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String profiles = Arrays.toString(environment.getActiveProfiles());
        for (String key : SECRET_KEYS) {
            String value = environment.getProperty(key);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (looksLikePlaceholder(value) || BANNED_SECRET_SHA256.contains(sha256Hex(value))) {
                throw new IllegalStateException(
                    "Refusing to start with profiles " + profiles + ": property '" + key
                        + "' is set to a known demo/placeholder value. Provide a real secret through secure "
                        + "environment configuration.");
            }
        }
        log.info("SecretsValidator passed for profiles {}", profiles);
    }

    private boolean looksLikePlaceholder(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.contains("replace-with")
            || normalized.contains("changeme")
            || normalized.contains("your-")
            || normalized.startsWith("demo-")
            || normalized.equals("secret")
            || normalized.equals("password");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to hash secret for validation", exception);
        }
    }
}
