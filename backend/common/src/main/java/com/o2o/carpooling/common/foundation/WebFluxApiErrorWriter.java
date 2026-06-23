package com.o2o.carpooling.common.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class WebFluxApiErrorWriter {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    public Mono<Void> write(
        ServerWebExchange exchange,
        HttpStatus status,
        String errorCode,
        String message
    ) {
        return write(exchange, status, errorCode, message, Map.of());
    }

    public Mono<Void> write(
        ServerWebExchange exchange,
        HttpStatus status,
        String errorCode,
        String message,
        Map<String, Object> details
    ) {
        String traceId = ensureTraceId(exchange);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceIdFilter.TRACE_ID_HEADER, traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(
                ApiError.of(status.value(), errorCode, message, traceId, details)
            );
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    public String ensureTraceId(ServerWebExchange exchange) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        String traceId = StringUtils.hasText(incoming) && incoming.length() <= 128
            ? incoming
            : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(TraceIdFilter.TRACE_ID_HEADER, traceId);
        return traceId;
    }
}
