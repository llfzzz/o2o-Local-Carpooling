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
    private final FixedWindowRateLimiter rateLimiter;
    private final WebFluxApiErrorWriter errorWriter;
    private final SecurityProperties properties;
    private final MeterRegistry meterRegistry;

    GatewaySecurityFilter(
        JwtTokenService jwtTokenService,
        FixedWindowRateLimiter rateLimiter,
        WebFluxApiErrorWriter errorWriter,
        SecurityProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.jwtTokenService = jwtTokenService;
        this.rateLimiter = rateLimiter;
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
            if (!allowRequest(exchange, path, null)) {
                return tooManyRequests(exchange, path);
            }
            return chain.filter(stripSpoofedHeaders(exchange, traceId, null));
        }

        JwtToken token;
        try {
            token = jwtTokenService.parse(requireBearerToken(exchange));
        } catch (InvalidTokenException exception) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "missing or invalid bearer token");
        }

        if (!allowRequest(exchange, path, token)) {
            return tooManyRequests(exchange, path);
        }
        if (requiresOperator(method, path) && !token.principal().hasAnyRole(UserRole.OPERATOR, UserRole.ADMIN)) {
            return errorWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "insufficient role");
        }
        return chain.filter(stripSpoofedHeaders(exchange, traceId, token));
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
    private boolean allowRequest(ServerWebExchange exchange, String path, JwtToken token) {
        String bucket = bucketFor(path);
        SecurityProperties.Window window = windowFor(path);
        String identity = token == null ? clientIp(exchange) : token.principal().userId();
        boolean allowed = rateLimiter.allow("gateway:" + bucket + ":" + identity, window.getCount(), window.getPeriod());
        // Low-cardinality only: bucket (auth/api/map) x outcome (allowed/rejected). Never the
        // identity/IP/user — that would explode series count and leak PII into metric labels.
        meterRegistry.counter("gateway.ratelimit.decisions", "bucket", bucket, "outcome", allowed ? "allowed" : "rejected")
            .increment();
        return allowed;
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

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String path) {
        // Tell the client how long to back off — the worst case is one full window.
        long retryAfterSeconds = Math.max(1, windowFor(path).getPeriod().toSeconds());
        exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
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
