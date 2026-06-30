package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and rotates opaque refresh tokens with reuse detection. Only token hashes are stored.
 * Rotating a token advances the session family's current token; presenting a previously rotated
 * token is treated as theft and revokes the whole family (forcing re-login).
 */
@Service
class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    record IssuedToken(String token, Instant expiresAt) {
    }

    record RotatedToken(String userId, String token, Instant expiresAt) {
    }

    private final RefreshTokenStore store;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenService(RefreshTokenStore store, RefreshTokenProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    IssuedToken issue(String userId) {
        String token = randomToken();
        String familyId = UUID.randomUUID().toString();
        String hash = hash(token);
        store.saveToken(hash, userId, familyId, properties.getTokenValidity());
        store.setFamilyCurrent(familyId, hash, properties.getTokenValidity());
        return new IssuedToken(token, clock.instant().plus(properties.getTokenValidity()));
    }

    RotatedToken rotate(String presentedToken) {
        if (!StringUtils.hasText(presentedToken)) {
            throw invalid();
        }
        String hash = hash(presentedToken);
        RefreshTokenStore.RefreshRecord record = store.findToken(hash).orElseThrow(this::invalid);
        String current = store.familyCurrent(record.familyId()).orElseThrow(this::invalid);
        if (!MessageDigest.isEqual(current.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))) {
            // A rotated-away token was replayed: treat as compromise and revoke the session family.
            store.deleteFamily(record.familyId());
            log.warn("refresh.reuse.detected userId={} familyId={} — session revoked", record.userId(), record.familyId());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE",
                "refresh token reuse detected; please sign in again");
        }
        String newToken = randomToken();
        String newHash = hash(newToken);
        store.saveToken(newHash, record.userId(), record.familyId(), properties.getTokenValidity());
        store.setFamilyCurrent(record.familyId(), newHash, properties.getTokenValidity());
        return new RotatedToken(record.userId(), newToken, clock.instant().plus(properties.getTokenValidity()));
    }

    void revoke(String presentedToken) {
        if (!StringUtils.hasText(presentedToken)) {
            return;
        }
        store.findToken(hash(presentedToken)).ifPresent(record -> store.deleteFamily(record.familyId()));
    }

    private BusinessException invalid() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID",
            "refresh token is invalid or expired");
    }

    private String randomToken() {
        byte[] material = new byte[32];
        random.nextBytes(material);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(material);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to hash refresh token", exception);
        }
    }
}
