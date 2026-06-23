package com.o2o.carpooling.common.foundation;

import com.o2o.carpooling.common.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class JwtTokenService {

    private static final String ROLES_CLAIM = "roles";

    private final SecurityProperties.Jwt properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtTokenService(SecurityProperties properties, Clock clock) {
        this.properties = properties.getJwt();
        this.clock = clock;
        this.signingKey = signingKey(this.properties.getBase64Secret());
    }

    public String createToken(SecurityPrincipal principal) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getTokenValidity());
        List<String> roles = principal.roles().stream().map(Enum::name).sorted().toList();
        return Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(principal.userId())
            .id(UUID.randomUUID().toString())
            .issuedAt(java.util.Date.from(issuedAt))
            .expiration(java.util.Date.from(expiresAt))
            .claim(ROLES_CLAIM, roles)
            .signWith(signingKey, Jwts.SIG.HS512)
            .compact();
    }

    public JwtToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> java.util.Date.from(clock.instant()))
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String subject = claims.getSubject();
            return new JwtToken(
                new SecurityPrincipal(subject, roles(claims.get(ROLES_CLAIM))),
                subject,
                claims.getId(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException exception) {
            throw new InvalidTokenException("token expired", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("token invalid", exception);
        }
    }

    private Set<UserRole> roles(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw new InvalidTokenException("token invalid: roles claim is missing");
        }
        return collection.stream()
            .map(Object::toString)
            .map(UserRole::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    private SecretKey signingKey(String secret) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("security.jwt.base64-secret must be a valid Base64 HS512 key", exception);
        }
    }
}
