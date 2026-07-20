package com.o2o.carpooling.trip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.GeoPoint;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Cross-instance presence store.
 *
 * <p>Redis key TTL is the expiry mechanism: a driver who stops reporting vanishes on its own, so
 * "offline" needs no sweeper job and a stale position can never be served as live. The same TTL
 * doubles as the retention limit — nothing about a driver's whereabouts outlives it.
 */
class RedisDriverPresenceStore implements DriverPresenceStore {

    private static final String KEY_PREFIX = "trip:driver-location:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration ttl;

    RedisDriverPresenceStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public void put(DriverLocation location) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("tripId", location.tripId());
        node.put("driverId", location.driverId());
        node.put("lat", location.point().latitude());
        node.put("lng", location.point().longitude());
        node.put("datum", location.point().datum().name());
        node.put("capturedAt", location.capturedAt().toString());
        if (location.headingDegrees() != null) {
            node.put("heading", location.headingDegrees());
        }
        if (location.speedMetersPerSecond() != null) {
            node.put("speed", location.speedMetersPerSecond());
        }
        try {
            redisTemplate.opsForValue().set(key(location.tripId()), objectMapper.writeValueAsString(node), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize driver location", exception);
        }
    }

    @Override
    public Optional<DriverLocation> find(String tripId) {
        String raw = redisTemplate.opsForValue().get(key(tripId));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return Optional.of(new DriverLocation(
                node.path("tripId").asText(),
                node.path("driverId").asText(),
                new GeoPoint(
                    node.path("lat").asDouble(),
                    node.path("lng").asDouble(),
                    CoordinateDatum.valueOf(node.path("datum").asText(CoordinateDatum.GCJ02.name()))),
                node.hasNonNull("heading") ? node.path("heading").asDouble() : null,
                node.hasNonNull("speed") ? node.path("speed").asDouble() : null,
                Instant.parse(node.path("capturedAt").asText())
            ));
        } catch (Exception exception) {
            // A corrupt entry must not break the stream; treat it as absent.
            return Optional.empty();
        }
    }

    @Override
    public void clear(String tripId) {
        redisTemplate.delete(key(tripId));
    }

    private String key(String tripId) {
        return KEY_PREFIX + tripId;
    }
}
