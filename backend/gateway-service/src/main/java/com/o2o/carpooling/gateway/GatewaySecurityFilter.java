package com.o2o.carpooling.gateway;

import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import com.o2o.carpooling.common.foundation.InvalidTokenException;
import com.o2o.carpooling.common.foundation.JwtToken;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityProperties;
import com.o2o.carpooling.common.foundation.TraceIdFilter;
import com.o2o.carpooling.common.foundation.WebFluxApiErrorWriter;
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

    GatewaySecurityFilter(
        JwtTokenService jwtTokenService,
        FixedWindowRateLimiter rateLimiter,
        WebFluxApiErrorWriter errorWriter,
        SecurityProperties properties
    ) {
        this.jwtTokenService = jwtTokenService;
        this.rateLimiter = rateLimiter;
        this.errorWriter = errorWriter;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = errorWriter.ensureTraceId(exchange);
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(stripSpoofedHeaders(exchange, traceId, null));
        }
        if (isPublicPath(path)) {
            if (!allowRequest(exchange, path, null)) {
                return tooManyRequests(exchange);
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
            return tooManyRequests(exchange);
        }
        if (requiresOperator(path) && !token.principal().hasAnyRole(UserRole.OPERATOR, UserRole.ADMIN)) {
            return errorWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "insufficient role");
        }
        return chain.filter(stripSpoofedHeaders(exchange, traceId, token));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private boolean allowRequest(ServerWebExchange exchange, String path, JwtToken token) {
        SecurityProperties.Window window = isAuthPath(path) ? properties.getRateLimit().getAuth() : properties.getRateLimit().getApi();
        String identity = token == null ? clientIp(exchange) : token.principal().userId();
        return rateLimiter.allow("gateway:" + (isAuthPath(path) ? "auth:" : "api:") + identity, window.getCount(), window.getPeriod());
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "request rate limit exceeded");
    }

    private boolean isPublicPath(String path) {
        return isAuthPath(path) || path.equals("/actuator/health") || path.equals("/actuator/info");
    }

    private boolean isAuthPath(String path) {
        return path.startsWith("/api/auth/");
    }

    private boolean requiresOperator(String path) {
        return path.startsWith("/api/admin/")
            || path.startsWith("/api/audits/")
            || path.equals("/api/orders/admin")
            || path.startsWith("/api/orders/admin/");
    }

    private String requireBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(properties.getJwt().getHeader());
        String prefix = properties.getJwt().getTokenStartWith() + " ";
        if (!StringUtils.hasText(header) || !header.startsWith(prefix)) {
            throw new InvalidTokenException("token invalid");
        }
        return header.substring(prefix.length());
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
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
