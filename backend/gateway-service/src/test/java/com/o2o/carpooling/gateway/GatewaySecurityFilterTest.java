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
    void rejectsRiderFromDemoControlRoutes() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/demo/control/notification/ntf-1/status", token(Set.of(UserRole.RIDER)));

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsRiderFromOcrTaskListing() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/ai/ocr/tasks", token(Set.of(UserRole.RIDER)));

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allowsOperatorToOcrTaskListingAndRiderToSingleTask() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange operatorList = exchange("/api/ai/ocr/tasks", token(Set.of(UserRole.OPERATOR)));
        MockServerWebExchange riderSingle = exchange("/api/ai/ocr/tasks/ocr-1", token(Set.of(UserRole.RIDER)));
        AtomicReference<Boolean> listForwarded = new AtomicReference<>(false);
        AtomicReference<Boolean> singleForwarded = new AtomicReference<>(false);

        filter.filter(operatorList, chain(unused -> listForwarded.set(true))).block();
        filter.filter(riderSingle, chain(unused -> singleForwarded.set(true))).block();

        assertThat(operatorList.getResponse().getStatusCode()).isNull();
        assertThat(listForwarded.get()).isTrue();
        assertThat(riderSingle.getResponse().getStatusCode()).isNull();
        assertThat(singleForwarded.get()).isTrue();
    }

    @Test
    void allowsAuthenticatedRiderToOwnDemoInbox() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/demo/inbox", token(Set.of(UserRole.RIDER)));
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(exchange, chain(unused -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isTrue();
    }

    @Test
    void allowsPaymentCallbackWithoutBearerToken() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = postExchange("/api/payments/callbacks/demo", null);
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(exchange, chain(unused -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isTrue();
    }

    @Test
    void blocksExternalUserUpsertToPreventRoleSelfAssignment() {
        // POST /api/users upserts a user record including its roles and is only meant for the
        // in-mesh auth->user Feign call. External reach would let any authenticated caller grant
        // themselves ADMIN, so the Gateway answers 404 (indistinguishable from "does not exist").
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = postExchange("/api/users", token(Set.of(UserRole.RIDER)));
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(exchange, chain(unused -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forwarded.get()).isFalse();
    }

    @Test
    void blocksExternalSingleUserLookup() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = exchange("/api/users/user-1", token(Set.of(UserRole.RIDER)));

        filter.filter(exchange, unused()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void blocksExternalOrderPayAndTimeout() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange pay = postExchange("/api/orders/order-1/pay", token(Set.of(UserRole.RIDER)));
        MockServerWebExchange timeout = postExchange("/api/orders/order-1/timeout", token(Set.of(UserRole.RIDER)));

        filter.filter(pay, unused()).block();
        filter.filter(timeout, unused()).block();

        assertThat(pay.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(timeout.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void allowsOrderCancelForAuthenticatedRider() {
        // Regression guard: only /pay and /timeout are internal-only; the user-facing cancel action
        // under the same /api/orders/{id}/ prefix must still be forwarded.
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange exchange = postExchange("/api/orders/order-1/cancel", token(Set.of(UserRole.RIDER)));
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(exchange, chain(unused -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isTrue();
    }

    @Test
    void blocksExternalSeatLockAndLegacySimulation() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange seatLock = postExchange("/api/trips/trip-1/seat-locks", token(Set.of(UserRole.RIDER)));
        MockServerWebExchange simulation = postExchange("/api/payments/simulations", token(Set.of(UserRole.RIDER)));

        filter.filter(seatLock, unused()).block();
        filter.filter(simulation, unused()).block();

        assertThat(seatLock.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(simulation.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsRiderFromDriverReviewButAllowsSelfServiceSubmit() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange approve = postExchange("/api/drivers/verification-cases/case-1/approve", token(Set.of(UserRole.RIDER)));
        MockServerWebExchange list = exchange("/api/drivers/verification-cases", token(Set.of(UserRole.RIDER)));
        MockServerWebExchange submit = postExchange("/api/drivers/verification-cases", token(Set.of(UserRole.RIDER)));
        AtomicReference<Boolean> submitForwarded = new AtomicReference<>(false);

        filter.filter(approve, unused()).block();
        filter.filter(list, unused()).block();
        filter.filter(submit, chain(unused -> submitForwarded.set(true))).block();

        assertThat(approve.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(list.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(submit.getResponse().getStatusCode()).isNull();
        assertThat(submitForwarded.get()).isTrue();
    }

    @Test
    void allowsOperatorToApproveDriverReview() {
        GatewaySecurityFilter filter = filter(new SecurityProperties());
        MockServerWebExchange approve = postExchange("/api/drivers/verification-cases/case-1/approve", token(Set.of(UserRole.OPERATOR)));
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(approve, chain(unused -> forwarded.set(true))).block();

        assertThat(approve.getResponse().getStatusCode()).isNull();
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

    @Test
    void mapCallsDrawFromTheirOwnQuotaBucketNotTheGeneralApiAllowance() {
        // Each map call costs external provider quota, so it must not share the API budget.
        SecurityProperties properties = new SecurityProperties();
        properties.getRateLimit().setMap(new SecurityProperties.Window(2, Duration.ofSeconds(60)));
        properties.getRateLimit().setApi(new SecurityProperties.Window(100, Duration.ofSeconds(60)));
        GatewaySecurityFilter filter = filter(properties);
        String riderToken = token(Set.of(UserRole.RIDER));

        assertThat(statusOf(filter, "/api/maps/place/suggest", riderToken)).isNull();
        assertThat(statusOf(filter, "/api/maps/place/suggest", riderToken)).isNull();
        // Third map call exhausts the map bucket...
        assertThat(statusOf(filter, "/api/maps/place/suggest", riderToken))
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // ...while ordinary API traffic is unaffected, because the buckets are separate.
        assertThat(statusOf(filter, "/api/orders", riderToken)).isNull();
    }

    @Test
    void theLegacyMapPathPrefixSharesTheMapBucket() {
        SecurityProperties properties = new SecurityProperties();
        properties.getRateLimit().setMap(new SecurityProperties.Window(1, Duration.ofSeconds(60)));
        GatewaySecurityFilter filter = filter(properties);
        String riderToken = token(Set.of(UserRole.RIDER));

        assertThat(statusOf(filter, "/api/maps/route", riderToken)).isNull();
        assertThat(statusOf(filter, "/api/map/route", riderToken)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private HttpStatus statusOf(GatewaySecurityFilter filter, String path, String token) {
        MockServerWebExchange exchange = exchange(path, token);
        filter.filter(exchange, chain(unused -> {
        })).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
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
