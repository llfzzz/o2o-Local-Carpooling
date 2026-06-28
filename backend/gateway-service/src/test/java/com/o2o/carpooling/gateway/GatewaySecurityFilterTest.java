package com.o2o.carpooling.gateway;

import com.o2o.carpooling.common.domain.UserRole;
import com.o2o.carpooling.common.foundation.InMemoryFixedWindowRateLimiter;
import com.o2o.carpooling.common.foundation.JwtTokenService;
import com.o2o.carpooling.common.foundation.SecurityPrincipal;
import com.o2o.carpooling.common.foundation.SecurityProperties;
import com.o2o.carpooling.common.foundation.WebFluxApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityFilterTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";

    @Test
    void rejectsProtectedApiWithoutBearerToken() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/orders");

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isNotBlank();
    }

    @Test
    void rejectsRiderFromAdminRoutes() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/admin/dashboard", token(Set.of(UserRole.RIDER)));

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsRiderFromOrderAdminRoutes() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/orders/admin", token(Set.of(UserRole.RIDER)));

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsRiderFromAuditRootRoute() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/audits", token(Set.of(UserRole.RIDER)));

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsRiderFromUserDirectory() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/users", token(Set.of(UserRole.RIDER)));

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allowsUserRegistrationPostForNonOperator() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = postExchange("/api/users", token(Set.of(UserRole.RIDER)));
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(exchange, chain(unused -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isTrue();
    }

    @Test
    void forwardsPrincipalHeadersForOperatorAndRemovesSpoofedInboundValues() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange(
            "/api/admin/dashboard",
            token(Set.of(UserRole.OPERATOR)),
            builder -> builder.header("X-User-Id", "spoofed").header("X-Trace-Id", "trace-in")
        );
        AtomicReference<HttpHeaders> forwardedHeaders = new AtomicReference<>();

        filter.filter(exchange, chain(captured -> forwardedHeaders.set(captured.getRequest().getHeaders()))).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwardedHeaders.get().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwardedHeaders.get().getFirst("X-User-Roles")).isEqualTo("OPERATOR");
        assertThat(forwardedHeaders.get().getFirst("X-Trace-Id")).isEqualTo("trace-in");
    }

    @Test
    void rateLimitsRepeatedAuthRequestsByIp() {
        SecurityProperties properties = new SecurityProperties();
        properties.getRateLimit().getAuth().setCount(1);
        GatewaySecurityFilter filter = filter(properties);

        filter.filter(exchange("/api/auth/login"), unused()).block();
        MockServerWebExchange second = exchange("/api/auth/login");
        filter.filter(second, unused()).block();

        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getResponse().getHeaders().getFirst("X-Trace-Id")).isNotBlank();
    }

    @Test
    void allowsCorsPreflightWithoutBearerToken() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = optionsExchange("/api/orders");
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(exchange, chain(unused -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isTrue();
    }

    private GatewaySecurityFilter filter(SecurityProperties properties) {
        properties.getJwt().setBase64Secret(SECRET);
        return new GatewaySecurityFilter(
            new JwtTokenService(properties, Clock.fixed(Instant.parse("2026-06-23T04:00:00Z"), ZoneOffset.UTC)),
            new InMemoryFixedWindowRateLimiter(Clock.fixed(Instant.parse("2026-06-23T04:00:00Z"), ZoneOffset.UTC)),
            new WebFluxApiErrorWriter(),
            properties
        );
    }

    private String token(Set<UserRole> roles) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setBase64Secret(SECRET);
        properties.getJwt().setTokenValidity(Duration.ofHours(2));
        return new JwtTokenService(properties, Clock.fixed(Instant.parse("2026-06-23T04:00:00Z"), ZoneOffset.UTC))
            .createToken(new SecurityPrincipal("user-1", roles));
    }

    private MockServerWebExchange exchange(String path) {
        return exchange(path, null);
    }

    private MockServerWebExchange exchange(String path, String token) {
        return exchange(path, token, builder -> {
        });
    }

    private MockServerWebExchange exchange(String path, String token, RequestCustomizer customizer) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path)
            .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        customizer.customize(builder);
        return MockServerWebExchange.from(builder);
    }

    private MockServerWebExchange optionsExchange(String path) {
        return MockServerWebExchange.from(
            MockServerHttpRequest.options(path)
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345))
        );
    }

    private MockServerWebExchange postExchange(String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post(path)
            .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(builder);
    }

    private GatewayFilterChain unused() {
        return exchange -> Mono.empty();
    }

    private GatewayFilterChain chain(java.util.function.Consumer<ServerWebExchange> consumer) {
        return exchange -> {
            consumer.accept(exchange);
            return Mono.empty();
        };
    }

    private interface RequestCustomizer {
        void customize(MockServerHttpRequest.BaseBuilder<?> builder);
    }
}
