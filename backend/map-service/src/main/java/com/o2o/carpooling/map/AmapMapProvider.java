package com.o2o.carpooling.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AMap (高德) web-service adapter.
 *
 * <p>Everything AMap returns is GCJ-02, so no datum conversion happens here — conversion belongs at
 * the request boundary in {@link MapQueryService}, before a coordinate ever reaches this class.
 *
 * <p>Fails closed throughout: a missing key, a non-"1" status, or an unparseable payload raises
 * {@code MAP_ROUTE_QUOTE_FAILED} rather than degrading to demo output.
 */
@Component
class AmapMapProvider implements MapProvider {

    private static final CoordinateDatum PROVIDER_DATUM = CoordinateDatum.GCJ02;
    private static final String PROVIDER = "amap";

    private final AmapProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AmapMapProvider(AmapProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public RouteQuoteResult quote(RouteQuoteRequest request) {
        requireApiKey();

        JsonNode originGeocode = null;
        JsonNode destinationGeocode = null;
        LocationRef originRef = request.originRef();
        LocationRef destinationRef = request.destinationRef();

        if (originRef == null) {
            originGeocode = geocode(request.origin(), request.city());
            originRef = firstGeocodedLocation(originGeocode, request.origin(), "origin");
        }
        if (destinationRef == null) {
            destinationGeocode = geocode(request.destination(), request.city());
            destinationRef = firstGeocodedLocation(destinationGeocode, request.destination(), "destination");
        }

        JsonNode driving = driving(originRef.point(), destinationRef.point());
        JsonNode path = firstPath(driving);
        int distanceMeters = parsePositiveInt(path.path("distance").asText(), "route distance");
        int durationSeconds = parsePositiveInt(durationNode(path).asText(), "route duration");

        RouteSnapshot route = new RouteSnapshot(
            "route-" + UUID.randomUUID(),
            distanceMeters,
            durationSeconds,
            "amap-v5",
            polyline(path),
            originRef,
            destinationRef
        );

        return RouteQuoteResult.from(
            request,
            route,
            PROVIDER,
            originRef.point().toProviderLngLat(),
            destinationRef.point().toProviderLngLat(),
            responseSnapshot(originGeocode, destinationGeocode, driving)
        );
    }

    @Override
    public LocationRef reverseGeocode(GeoPoint point) {
        requireApiKey();
        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/v3/geocode/regeo")
                .queryParam("key", properties.getApiKey())
                .queryParam("location", point.toProviderLngLat())
                .queryParam("extensions", "base")
                .queryParam("output", "json")
                .build())
            .retrieve()
            .body(JsonNode.class);
        validateStatus(response, "amap reverse geocode failed");

        JsonNode regeocode = response.path("regeocode");
        JsonNode component = regeocode.path("addressComponent");
        String formatted = regeocode.path("formatted_address").asText("");
        if (!StringUtils.hasText(formatted)) {
            throw routeQuoteFailed("amap reverse geocode returned no address");
        }
        String adcode = component.path("adcode").asText("");
        if (!StringUtils.hasText(adcode)) {
            throw routeQuoteFailed("amap reverse geocode returned no adcode");
        }

        return new LocationRef(
            point,
            PROVIDER,
            null,
            textOrNull(component.path("citycode")),
            adcode,
            nearbyLabel(regeocode, component, formatted),
            formatted,
            LocationSource.MAP_PIN,
            null,
            Instant.now()
        );
    }

    @Override
    public List<LocationRef> suggest(PlaceQuery query) {
        requireApiKey();
        JsonNode response = restClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/v3/assistant/inputtips")
                    .queryParam("key", properties.getApiKey())
                    .queryParam("keywords", query.keyword())
                    .queryParam("datatype", "poi");
                if (StringUtils.hasText(query.cityCode())) {
                    builder.queryParam("city", query.cityCode()).queryParam("citylimit", "true");
                }
                if (query.bias() != null) {
                    builder.queryParam("location", query.bias().toProviderLngLat());
                }
                return builder.queryParam("output", "json").build();
            })
            .retrieve()
            .body(JsonNode.class);
        validateStatus(response, "amap input tips failed");

        List<LocationRef> results = new ArrayList<>();
        for (JsonNode tip : response.path("tips")) {
            // Tips without a location are administrative suggestions, not places we can route to.
            String location = tip.path("location").asText("");
            String adcode = tip.path("adcode").asText("");
            if (!StringUtils.hasText(location) || !StringUtils.hasText(adcode)) {
                continue;
            }
            results.add(new LocationRef(
                GeoPoint.parseProviderLngLat(location, PROVIDER_DATUM),
                PROVIDER,
                textOrNull(tip.path("id")),
                null,
                adcode,
                tip.path("name").asText("未命名地点"),
                describeTipAddress(tip),
                LocationSource.AUTOCOMPLETE,
                null,
                Instant.now()
            ));
            if (results.size() >= query.size()) {
                break;
            }
        }
        return List.copyOf(results);
    }

    @Override
    public List<LocationRef> searchPoi(PlaceQuery query) {
        requireApiKey();
        JsonNode response = restClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/v5/place/text")
                    .queryParam("key", properties.getApiKey())
                    .queryParam("keywords", query.keyword())
                    .queryParam("page_size", query.size())
                    .queryParam("page_num", 1);
                if (StringUtils.hasText(query.cityCode())) {
                    builder.queryParam("region", query.cityCode()).queryParam("city_limit", "true");
                }
                if (query.bias() != null) {
                    builder.queryParam("location", query.bias().toProviderLngLat());
                }
                return builder.queryParam("output", "json").build();
            })
            .retrieve()
            .body(JsonNode.class);
        validateStatus(response, "amap poi search failed");

        List<LocationRef> results = new ArrayList<>();
        for (JsonNode poi : response.path("pois")) {
            String location = poi.path("location").asText("");
            String adcode = poi.path("adcode").asText("");
            if (!StringUtils.hasText(location) || !StringUtils.hasText(adcode)) {
                continue;
            }
            results.add(new LocationRef(
                GeoPoint.parseProviderLngLat(location, PROVIDER_DATUM),
                PROVIDER,
                textOrNull(poi.path("id")),
                textOrNull(poi.path("citycode")),
                adcode,
                poi.path("name").asText("未命名地点"),
                poi.path("address").asText(poi.path("name").asText("")),
                LocationSource.POI_SEARCH,
                null,
                Instant.now()
            ));
        }
        return List.copyOf(results);
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

    private JsonNode driving(GeoPoint origin, GeoPoint destination) {
        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/v5/direction/driving")
                .queryParam("key", properties.getApiKey())
                .queryParam("origin", origin.toProviderLngLat())
                .queryParam("destination", destination.toProviderLngLat())
                .queryParam("show_fields", "cost,polyline")
                .queryParam("output", "json")
                .build())
            .retrieve()
            .body(JsonNode.class);
        validateStatus(response, "amap driving route failed");
        return response;
    }

    private LocationRef firstGeocodedLocation(JsonNode response, String queryText, String label) {
        JsonNode geocodes = response.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw routeQuoteFailed("amap " + label + " geocode returned no location");
        }
        JsonNode first = geocodes.get(0);
        String location = first.path("location").asText("");
        String adcode = first.path("adcode").asText("");
        if (!StringUtils.hasText(location) || !StringUtils.hasText(adcode)) {
            throw routeQuoteFailed("amap " + label + " geocode returned invalid location");
        }
        return new LocationRef(
            GeoPoint.parseProviderLngLat(location, PROVIDER_DATUM),
            PROVIDER,
            null,
            textOrNull(first.path("citycode")),
            adcode,
            queryText,
            first.path("formatted_address").asText(queryText),
            LocationSource.MANUAL,
            null,
            Instant.now()
        );
    }

    /** v5 reports duration under {@code cost}; v3 reported it on the path itself. */
    private JsonNode durationNode(JsonNode path) {
        JsonNode duration = path.path("cost").path("duration");
        if (duration.isMissingNode() || !StringUtils.hasText(duration.asText())) {
            return path.path("duration");
        }
        return duration;
    }

    /** Concatenates each step's geometry into a single {@code "lng,lat;lng,lat"} polyline. */
    private String polyline(JsonNode path) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode step : path.path("steps")) {
            String segment = step.path("polyline").asText("");
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(segment);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String nearbyLabel(JsonNode regeocode, JsonNode component, String fallback) {
        JsonNode poi = regeocode.path("pois");
        if (poi.isArray() && !poi.isEmpty()) {
            String name = poi.get(0).path("name").asText("");
            if (StringUtils.hasText(name)) {
                return name;
            }
        }
        String township = component.path("township").asText("");
        return StringUtils.hasText(township) ? township : fallback;
    }

    private String describeTipAddress(JsonNode tip) {
        String district = tip.path("district").asText("");
        JsonNode address = tip.path("address");
        String addressText = address.isArray() ? "" : address.asText("");
        String combined = (district + addressText).trim();
        return StringUtils.hasText(combined) ? combined : tip.path("name").asText("");
    }

    private JsonNode firstPath(JsonNode response) {
        JsonNode paths = response.path("route").path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            throw routeQuoteFailed("amap driving route returned no path");
        }
        return paths.get(0);
    }

    private void requireApiKey() {
        // Fail closed if selected (providers.map.type=amap) but the key is missing — never downgrade to demo.
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw routeQuoteFailed("AMAP_API_KEY is not configured");
        }
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

    private String textOrNull(JsonNode node) {
        String value = node.asText("");
        return StringUtils.hasText(value) ? value : null;
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

    private BusinessException routeQuoteFailed(String message) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "MAP_ROUTE_QUOTE_FAILED", message);
    }
}
