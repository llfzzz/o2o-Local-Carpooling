package com.o2o.carpooling.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
class AmapRouteProvider implements MapRouteProvider {

    private static final Pattern COORDINATE = Pattern.compile("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?");

    private final AmapProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AmapRouteProvider(AmapProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String name() {
        return "amap";
    }

    @Override
    public RouteQuoteResult quote(RouteQuoteRequest request) {
        // Fail closed if selected (providers.map.type=amap) but the key is missing — never downgrade to mock.
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw routeQuoteFailed("AMAP_API_KEY is not configured");
        }

        JsonNode originGeocode = null;
        JsonNode destinationGeocode = null;
        String originCoordinate = request.origin();
        String destinationCoordinate = request.destination();
        if (!isCoordinate(originCoordinate)) {
            originGeocode = geocode(request.origin(), request.city());
            originCoordinate = firstLocation(originGeocode, "origin");
        }
        if (!isCoordinate(destinationCoordinate)) {
            destinationGeocode = geocode(request.destination(), request.city());
            destinationCoordinate = firstLocation(destinationGeocode, "destination");
        }

        JsonNode driving = driving(originCoordinate, destinationCoordinate);
        JsonNode path = firstPath(driving);
        int distanceMeters = parsePositiveInt(path.path("distance").asText(), "route distance");
        JsonNode durationNode = path.path("cost").path("duration");
        if (durationNode.isMissingNode() || !StringUtils.hasText(durationNode.asText())) {
            durationNode = path.path("duration");
        }
        int durationSeconds = parsePositiveInt(durationNode.asText(), "route duration");
        RouteSnapshot route = new RouteSnapshot("route-" + UUID.randomUUID(), distanceMeters, durationSeconds, "amap-v5");

        return RouteQuoteResult.from(
            request,
            route,
            "amap-v5",
            originCoordinate,
            destinationCoordinate,
            responseSnapshot(originGeocode, destinationGeocode, driving)
        );
    }

    private JsonNode geocode(String address, String city) {
        JsonNode response = restClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/v3/geocode/geo")
                    .queryParam("key", properties.getApiKey())
                    .queryParam("address", address);
                if (StringUtils.hasText(city)) {
                    builder.queryParam("city", city);
                }
                return builder.queryParam("output", "json").build();
            })
            .retrieve()
            .body(JsonNode.class);
        validateStatus(response, "amap geocode failed");
        return response;
    }

    private JsonNode driving(String originCoordinate, String destinationCoordinate) {
        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/v5/direction/driving")
                .queryParam("key", properties.getApiKey())
                .queryParam("origin", originCoordinate)
                .queryParam("destination", destinationCoordinate)
                .queryParam("show_fields", "cost")
                .queryParam("output", "json")
                .build())
            .retrieve()
            .body(JsonNode.class);
        validateStatus(response, "amap driving route failed");
        return response;
    }

    private String firstLocation(JsonNode response, String label) {
        JsonNode geocodes = response.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw routeQuoteFailed("amap " + label + " geocode returned no location");
        }
        String location = geocodes.get(0).path("location").asText();
        if (!isCoordinate(location)) {
            throw routeQuoteFailed("amap " + label + " geocode returned invalid location");
        }
        return location;
    }

    private JsonNode firstPath(JsonNode response) {
        JsonNode paths = response.path("route").path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            throw routeQuoteFailed("amap driving route returned no path");
        }
        return paths.get(0);
    }

    private void validateStatus(JsonNode response, String message) {
        if (response == null || !"1".equals(response.path("status").asText())) {
            String info = response == null ? "empty response" : response.path("info").asText("unknown");
            throw routeQuoteFailed(message + ": " + info);
        }
    }

    private int parsePositiveInt(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException exception) {
            // handled below
        }
        throw routeQuoteFailed("amap returned invalid " + label);
    }

    private String responseSnapshot(JsonNode originGeocode, JsonNode destinationGeocode, JsonNode driving) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        if (originGeocode != null) {
            snapshot.set("originGeocode", originGeocode);
        }
        if (destinationGeocode != null) {
            snapshot.set("destinationGeocode", destinationGeocode);
        }
        snapshot.set("driving", driving);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw routeQuoteFailed("failed to serialize amap route snapshot");
        }
    }

    private boolean isCoordinate(String value) {
        return StringUtils.hasText(value) && COORDINATE.matcher(value.trim()).matches();
    }

    private BusinessException routeQuoteFailed(String message) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "MAP_ROUTE_QUOTE_FAILED", message);
    }
}
