package com.o2o.carpooling.gateway;

import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import com.o2o.carpooling.common.foundation.InvalidTokenException;
import com.o2o.carpooling.common.foundation.JwtToken;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityProperties;
import com.o2o.carpooling.common.foundation.TraceIdFilter;
import com.o2o.carpooling.common.foundation.WebFluxApiErrorWriter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
class GatewaySecurityFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    private final JwtTokenService jwtTokenService;
    private final GatewayRateLimiter primaryLimiter;
    private final FixedWindowRateLimiter emergencyLimiter;
    private final WebFluxApiErrorWriter errorWriter;
    private final SecurityProperties properties;
    private final MeterRegistry meterRegistry;

    GatewaySecurityFilter(
        JwtTokenService jwtTokenService,
        GatewayRateLimiter primaryLimiter,
        @Qualifier("gatewayEmergencyRateLimiter") FixedWindowRateLimiter emergencyLimiter,
        WebFluxApiErrorWriter errorWriter,
        SecurityProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.jwtTokenService = jwtTokenService;
        this.primaryLimiter = primaryLimiter;
        this.emergencyLimiter = emergencyLimiter;
        this.errorWriter = errorWriter;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = errorWriter.ensureTraceId(exchange);
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(stripSpoofedHeaders(exchange, traceId, null));
        }
        // Some backend endpoints exist only for service-to-service (Feign) calls, which reach the
        // service directly by URL and never traverse the Gateway. Any request for them arriving here
        // is by definition external, so refuse it — and answer 404 (not 403) so the internal-only
        // surface is indistinguishable from "does not exist". This closes external bypasses of the
        // authoritative flows: crediting a payment without the signed callback, self-assigning roles,
        // force-cancelling or seat-tampering another user's order.
        if (isInternalOnlyPath(method, path)) {
            return errorWriter.write(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "resource not found");
        }
        if (isPublicPath(path)) {
            return allow(exchange, path, null).flatMap(decision -> decision.allowed()
                ? chain.filter(stripSpoofedHeaders(exchange, traceId, null))
                : tooManyRequests(exchange, decision.retryAfterSeconds()));
        }

        JwtToken token;
        try {
            token = jwtTokenService.parse(requireBearerToken(exchange));
        } catch (InvalidTokenException exception) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "missing or invalid bearer token");
        }

        JwtToken parsed = token;
        return allow(exchange, path, parsed).flatMap(decision -> {
            if (!decision.allowed()) {
                return tooManyRequests(exchange, decision.retryAfterSeconds());
            }
            if (requiresOperator(method, path) && !parsed.principal().hasAnyRole(UserRole.OPERATOR, UserRole.ADMIN)) {
                return errorWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "insufficient role");
            }
            return chain.filter(stripSpoofedHeaders(exchange, traceId, parsed));
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    /**
     * Map endpoints get their own, tighter bucket: each call costs external provider quota, and
     * autocomplete fires on every debounced keystroke. Without this, one account could exhaust the
     * day's map quota from the general API allowance.
     */
    private Mono<RateLimitDecision> allow(ServerWebExchange exchange, String path, JwtToken token) {
        String bucket = bucketFor(path);
        SecurityProperties.Window window = windowFor(path);
        String identity = token == null ? clientIp(exchange) : token.principal().userId();
        String key = "gateway:" + bucket + ":" + identity;
        return primaryLimiter.allow(key, window.getCount(), window.getPeriod())
            .doOnNext(decision -> recordDecision(bucket, decision.allowed(), primaryLimiter.backendName()))
            .onErrorResume(redisFailure -> degraded(bucket, path, key, window));
    }

    /**
     * Redis-backend failure handling. Never "silently unlimited": either a bounded local in-memory
     * emergency limiter, or (when {@code fail-closed-when-degraded=true}) fail-closed for sensitive
     * buckets. Always metered so the loss of distributed limiting is visible.
     */
    private Mono<RateLimitDecision> degraded(String bucket, String path, String key, SecurityProperties.Window window) {
        meterRegistry.counter("gateway.ratelimit.degraded", "bucket", bucket).increment();
        long retryAfter = Math.max(1, window.getPeriod().toSeconds());
        if (properties.getRateLimit().isFailClosedWhenDegraded() && isSensitive(path)) {
            recordDecision(bucket, false, "degraded");
            return Mono.just(new RateLimitDecision(false, 0, retryAfter));
        }
        boolean allowed = emergencyLimiter.allow(key, window.getCount(), window.getPeriod());
        recordDecision(bucket, allowed, "degraded");
        return Mono.just(new RateLimitDecision(allowed, 0, retryAfter));
    }

    private void recordDecision(String bucket, boolean allowed, String backend) {
        // Low-cardinality only: bucket (auth/api/map) x outcome x backend (redis/memory/degraded).
        // Never the identity/IP/user — that would explode series count and leak PII into labels.
        meterRegistry.counter("gateway.ratelimit.decisions",
            "bucket", bucket, "outcome", allowed ? "allowed" : "rejected", "backend", backend).increment();
    }

    /** Buckets whose loss of limiting is most dangerous: auth, map/provider, payment callbacks, demo control. */
    private boolean isSensitive(String path) {
        return isAuthPath(path) || isMapPath(path) || isPaymentCallbackPath(path)
            || path.startsWith("/api/demo/control/");
    }

    private String bucketFor(String path) {
        if (isAuthPath(path)) {
            return "auth";
        }
        if (isMapPath(path)) {
            return "map";
        }
        return "api";
    }

    private SecurityProperties.Window windowFor(String path) {
        if (isAuthPath(path)) {
            return properties.getRateLimit().getAuth();
        }
        if (isMapPath(path)) {
            return properties.getRateLimit().getMap();
        }
        return properties.getRateLimit().getApi();
    }

    private boolean isMapPath(String path) {
        return path.startsWith("/api/maps/") || path.startsWith("/api/map/");
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, long retryAfterSeconds) {
        // Retry-After reflects the real window remainder from Redis (or the full window when degraded).
        exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfterSeconds)));
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "request rate limit exceeded");
    }

    private boolean isPublicPath(String path) {
        return isAuthPath(path)
            || isPaymentCallbackPath(path)
            || path.equals("/actuator/health")
            || path.equals("/actuator/info");
    }

    private boolean isAuthPath(String path) {
        return path.startsWith("/api/auth/");
    }

    /**
     * Provider payment webhooks: a PSP has no JWT, so these are authenticated by HMAC signature at
     * payment-sim-service rather than by a bearer token. Still rate-limited (by client IP) and
     * still has spoofed principal headers stripped like any other request.
     */
    private boolean isPaymentCallbackPath(String path) {
        return path.startsWith("/api/payments/callbacks/");
    }

    /**
     * Endpoints that only in-mesh Feign callers (which hit the service directly, bypassing the
     * Gateway) may use. They must never be reachable from outside, regardless of the caller's role:
     * <ul>
     *   <li>{@code POST /api/users} — upserts a user record including its roles; external reach would
     *       let any authenticated caller self-assign ADMIN.</li>
     *   <li>{@code /api/users/{id}} — returns a single full user record (unmasked); the external
     *       directory is the masked {@code GET /api/users} list only.</li>
     *   <li>{@code POST /api/orders/{id}/pay} — credits an order; payment must go through the signed
     *       callback pipeline, never a direct state flip.</li>
     *   <li>{@code POST /api/orders/{id}/timeout} — force-expires an order.</li>
     *   <li>{@code /api/trips/{id}/seat-locks...} — locks/releases seat inventory.</li>
     *   <li>{@code POST /api/payments/simulations|simulate-success} — legacy direct "mark paid".</li>
     * </ul>
     */
    private boolean isInternalOnlyPath(HttpMethod method, String path) {
        if (method == HttpMethod.POST && path.equals("/api/users")) {
            return true;
        }
        if (path.startsWith("/api/users/")) {
            return true;
        }
        if (method == HttpMethod.POST && path.startsWith("/api/orders/")
            && (path.endsWith("/pay") || path.endsWith("/timeout"))) {
            return true;
        }
        if (method == HttpMethod.POST && path.startsWith("/api/trips/") && path.contains("/seat-locks")) {
            return true;
        }
        return method == HttpMethod.POST
            && (path.equals("/api/payments/simulations") || path.equals("/api/payments/simulate-success"));
    }

    private boolean requiresOperator(HttpMethod method, String path) {
        if (method == HttpMethod.GET && path.equals("/api/users")) {
            // operator user directory (masked phones); /{id} lookup and POST registration stay open
            return true;
        }
        if (method == HttpMethod.GET && path.equals("/api/ai/ocr/tasks")) {
            // OCR task listing spans all tasks (they are not user-owned); console-only
            return true;
        }
        if (method == HttpMethod.GET && path.equals("/api/drivers/verification-cases")) {
            // operator review queue — spans all drivers' cases; self-service submit (POST) stays open
            return true;
        }
        if (method == HttpMethod.POST && path.startsWith("/api/drivers/verification-cases/")
            && (path.endsWith("/approve") || path.endsWith("/reject"))) {
            // approving/rejecting driver documents is an operator decision
            return true;
        }
        return path.startsWith("/api/admin/")
            || path.equals("/api/audits")
            || path.startsWith("/api/audits/")
            || path.equals("/api/orders/admin")
            || path.startsWith("/api/orders/admin/")
            || path.startsWith("/api/demo/control/");
    }

    private String requireBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(properties.getJwt().getHeader());
        String prefix = properties.getJwt().getTokenStartWith() + " ";
        if (!StringUtils.hasText(header) || !header.startsWith(prefix)) {
            throw new InvalidTokenException("token invalid");
        }
        return header.substring(prefix.length());
    }

    /**
     * The IP to rate-limit unauthenticated traffic by. Behind the on-host nginx every request's
     * socket peer is loopback, so keying on the socket address alone collapses all clients into one
     * bucket. When the peer is a trusted proxy, honour {@code X-Real-IP} (nginx overwrites it with the
     * real client peer — a client cannot forge it through the proxy), then the left-most
     * {@code X-Forwarded-For} entry. Otherwise use the socket address (direct, no proxy).
     */
    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String peer = (remoteAddress == null || remoteAddress.getAddress() == null)
            ? "unknown" : remoteAddress.getAddress().getHostAddress();
        if (isTrustedProxy(remoteAddress)) {
            HttpHeaders headers = exchange.getRequest().getHeaders();
            String realIp = headers.getFirst("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
            String forwardedFor = headers.getFirst("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                int comma = forwardedFor.indexOf(',');
                String client = comma < 0 ? forwardedFor : forwardedFor.substring(0, comma);
                if (StringUtils.hasText(client)) {
                    return client.trim();
                }
            }
        }
        return peer;
    }

    private boolean isTrustedProxy(InetSocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return false;
        }
        if (remoteAddress.getAddress().isLoopbackAddress()) {
            return true; // the on-host nginx that fronts the gateway
        }
        return properties.getRateLimit().getTrustedProxies().contains(remoteAddress.getAddress().getHostAddress());
    }

    private ServerWebExchange stripSpoofedHeaders(ServerWebExchange exchange, String traceId, JwtToken token) {
        return exchange.mutate()
            .request(builder -> {
                builder.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                    headers.remove(TraceIdFilter.TRACE_ID_HEADER);
                    headers.remove(HttpHeaders.AUTHORIZATION);
                });
                builder.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
                if (token != null) {
                    builder.header(USER_ID_HEADER, token.principal().userId());
                    builder.header(USER_ROLES_HEADER, token.principal().rolesHeaderValue());
                }
            })
            .build();
    }
}
