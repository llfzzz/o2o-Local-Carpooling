package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.CoordinateTransform;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.Haversine;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapQueryServiceTest {

    @Test
    void convertsBrowserWgs84CoordinatesToProviderDatumBeforeCallingTheProvider() {
        RecordingProvider provider = new RecordingProvider();
        MapQueryService service = service(provider, new MapCityRegistry());

        // What navigator.geolocation would hand us.
        service.reverseGeocode(GeoPoint.wgs84(24.4879, 118.1781));

        GeoPoint seen = provider.lastReverseGeocoded;
        assertThat(seen.datum()).isEqualTo(CoordinateDatum.GCJ02);
        // Untransformed, this would land ~500m away — the exact bug this boundary exists to prevent.
        double shift = Haversine.distanceMeters(24.4879, 118.1781, seen.latitude(), seen.longitude());
        assertThat(shift).isBetween(300.0, 800.0);
        assertThat(seen).isEqualTo(CoordinateTransform.wgs84ToGcj02(GeoPoint.wgs84(24.4879, 118.1781)));
    }

    @Test
    void passesProviderDatumCoordinatesThroughUntouched() {
        RecordingProvider provider = new RecordingProvider();
        MapQueryService service = service(provider, new MapCityRegistry());

        GeoPoint fromAmapJsApi = GeoPoint.gcj02(24.4879, 118.1781);
        service.reverseGeocode(fromAmapJsApi);

        assertThat(provider.lastReverseGeocoded).isEqualTo(fromAmapJsApi);
    }

    @Test
    void convertsTheSearchBiasPointToo() {
        RecordingProvider provider = new RecordingProvider();
        MapQueryService service = service(provider, new MapCityRegistry());

        service.suggest(PlaceQuery.of("软件园", "0592", GeoPoint.wgs84(24.4879, 118.1781), 10));

        assertThat(provider.lastQuery.bias().datum()).isEqualTo(CoordinateDatum.GCJ02);
        assertThat(provider.lastQuery.keyword()).isEqualTo("软件园");
    }

    @Test
    void rejectsAReverseGeocodeOutsideTheSupportedCities() {
        RecordingProvider provider = new RecordingProvider();
        provider.reverseGeocodeResult = place("510104", "春熙路");
        MapQueryService service = service(provider, registryEnabling("3502"));

        assertThatThrownBy(() -> service.reverseGeocode(GeoPoint.gcj02(30.6516, 104.0817)))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("MAP_CITY_NOT_SUPPORTED"));
    }

    @Test
    void filtersSuggestionsOutsideTheAllowlistRatherThanFailingTheWholeQuery() {
        // A keyword legitimately matches places in several cities; only the served ones come back.
        RecordingProvider provider = new RecordingProvider();
        provider.suggestResults = List.of(
            place("350211", "集美大学"),
            place("510104", "四川大学"),
            place("350203", "厦门大学"));
        MapQueryService service = service(provider, registryEnabling("3502"));

        List<LocationRef> results = service.suggest(PlaceQuery.of("大学", null, null, 10));

        assertThat(results).extracting(LocationRef::displayName).containsExactly("集美大学", "厦门大学");
    }

    @Test
    void returnsEverySuggestionWhenNoAllowlistIsConfigured() {
        RecordingProvider provider = new RecordingProvider();
        provider.suggestResults = List.of(place("350211", "集美大学"), place("510104", "四川大学"));
        MapQueryService service = service(provider, new MapCityRegistry());

        assertThat(service.suggest(PlaceQuery.of("大学", null, null, 10))).hasSize(2);
    }

    @Test
    void reportsWhetherTheDemoProviderIsActiveSoTheClientCanBadgeResults() {
        RecordingProvider provider = new RecordingProvider();

        assertThat(service(provider, new MapCityRegistry(), "demo").isDemoActive()).isTrue();
        assertThat(service(provider, new MapCityRegistry(), "amap").isDemoActive()).isFalse();
    }

    private MapQueryService service(MapProvider provider, MapCityRegistry registry) {
        return service(provider, registry, "demo");
    }

    private MapQueryService service(MapProvider provider, MapCityRegistry registry, String type) {
        ProviderProperties properties = new ProviderProperties();
        properties.getMap().setType(type);
        return new MapQueryService(new MapProviderSelector(List.of(provider), properties), registry);
    }

    private MapCityRegistry registryEnabling(String adcodePrefix) {
        MapCityRegistry registry = new MapCityRegistry();
        MapCityRegistry.SupportedCity city = new MapCityRegistry.SupportedCity();
        city.setAdcodePrefix(adcodePrefix);
        city.setName("厦门");
        city.setCityCode("0592");
        registry.setEnabled(new ArrayList<>(List.of(city)));
        return registry;
    }

    private static LocationRef place(String adcode, String name) {
        return new LocationRef(
            GeoPoint.gcj02(24.4879, 118.1781), "demo", null, "0592", adcode, name, name,
            LocationSource.AUTOCOMPLETE, null, Instant.parse("2026-07-20T02:00:00Z"));
    }

    private static final class RecordingProvider implements MapProvider {

        private GeoPoint lastReverseGeocoded;
        private PlaceQuery lastQuery;
        private LocationRef reverseGeocodeResult = place("350211", "软件园三期");
        private List<LocationRef> suggestResults = List.of(place("350211", "软件园三期"));

        @Override
        public String name() {
            return "demo";
        }

        @Override
        public RouteQuoteResult quote(RouteQuoteRequest request) {
            throw new UnsupportedOperationException("not needed for query tests");
        }

        @Override
        public LocationRef reverseGeocode(GeoPoint point) {
            lastReverseGeocoded = point;
            return reverseGeocodeResult;
        }

        @Override
        public List<LocationRef> suggest(PlaceQuery query) {
            lastQuery = query;
            return suggestResults;
        }

        @Override
        public List<LocationRef> searchPoi(PlaceQuery query) {
            lastQuery = query;
            return suggestResults;
        }
    }
}
