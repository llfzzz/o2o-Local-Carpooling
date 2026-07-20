package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapMapProviderTest {

    private AmapProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new AmapProperties();
        properties.setApiKey("secret-key");
        properties.setBaseUrl("https://restapi.amap.test");
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build();
    }

    private AmapMapProvider provider() {
        return new AmapMapProvider(properties, restClientBuilder);
    }

    @Test
    void geocodesTextAddressesAndParsesDrivingRouteWithGeometry() {
        server.expect(once(), requestTo("https://restapi.amap.test/v3/geocode/geo?key=secret-key&address=%E8%BD%AF%E4%BB%B6%E5%9B%AD%E4%B8%89%E6%9C%9F&city=%E5%8E%A6%E9%97%A8&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","geocodes":[{"location":"118.178100,24.487900","adcode":"350211","citycode":"0592","formatted_address":"福建省厦门市集美区软件园三期"}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://restapi.amap.test/v3/geocode/geo?key=secret-key&address=%E9%9B%86%E7%BE%8E%E5%A4%A7%E5%AD%A6&city=%E5%8E%A6%E9%97%A8&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","geocodes":[{"location":"118.097200,24.575100","adcode":"350211","citycode":"0592","formatted_address":"福建省厦门市集美区集美大学"}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://restapi.amap.test/v5/direction/driving?key=secret-key&origin=118.1781,24.4879&destination=118.0972,24.5751&show_fields=cost,polyline&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","infocode":"10000","route":{"paths":[{"distance":"22500","cost":{"duration":"2600"},
                 "steps":[{"polyline":"118.1781,24.4879;118.1500,24.5100"},{"polyline":"118.1500,24.5100;118.0972,24.5751"}]}]}}
                """, MediaType.APPLICATION_JSON));

        RouteQuoteResult result = provider().quote(RouteQuoteRequest.ofText("软件园三期", "集美大学", "厦门"));
        RouteSnapshot route = result.routeSnapshot();

        assertThat(route.distanceMeters()).isEqualTo(22_500);
        assertThat(route.durationSeconds()).isEqualTo(2_600);
        assertThat(route.providerTrace()).isEqualTo("amap-v5");
        assertThat(route.hasGeometry()).isTrue();
        assertThat(route.polyline()).isEqualTo("118.1781,24.4879;118.1500,24.5100;118.1500,24.5100;118.0972,24.5751");
        assertThat(route.origin().adcode()).isEqualTo("350211");
        assertThat(route.origin().point().datum()).isEqualTo(CoordinateDatum.GCJ02);
        assertThat(result.providerResponseSnapshot()).doesNotContain("secret-key");
        server.verify();
    }

    @Test
    void skipsGeocodingWhenBothEndpointsAreAlreadyResolved() {
        server.expect(once(), requestTo("https://restapi.amap.test/v5/direction/driving?key=secret-key&origin=118.1781,24.4879&destination=118.0972,24.5751&show_fields=cost,polyline&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","route":{"paths":[{"distance":"22500","cost":{"duration":"2600"},"steps":[]}]}}
                """, MediaType.APPLICATION_JSON));

        RouteQuoteResult result = provider().quote(RouteQuoteRequest.ofLocations(
            resolved("软件园三期", 24.4879, 118.1781, "350211"),
            resolved("集美大学", 24.5751, 118.0972, "350211")));

        // No geocode calls expected at all — server.verify() fails if any were made.
        assertThat(result.routeSnapshot().distanceMeters()).isEqualTo(22_500);
        assertThat(result.routeSnapshot().polyline()).isNull();
        server.verify();
    }

    @Test
    void reverseGeocodesAPinIntoAStructuredPlace() {
        server.expect(once(), requestTo(
            "https://restapi.amap.test/v3/geocode/regeo?key=secret-key&location=118.1781,24.4879&extensions=base&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","regeocode":{"formatted_address":"福建省厦门市集美区软件园三期A区",
                 "addressComponent":{"adcode":"350211","citycode":"0592","township":"侨英街道"}}}
                """, MediaType.APPLICATION_JSON));

        LocationRef place = provider().reverseGeocode(GeoPoint.gcj02(24.4879, 118.1781));

        assertThat(place.adcode()).isEqualTo("350211");
        assertThat(place.cityCode()).isEqualTo("0592");
        assertThat(place.formattedAddress()).isEqualTo("福建省厦门市集美区软件园三期A区");
        assertThat(place.source()).isEqualTo(LocationSource.MAP_PIN);
        assertThat(place.provider()).isEqualTo("amap");
        assertThat(place.isDemo()).isFalse();
        server.verify();
    }

    @Test
    void suggestSkipsTipsWithoutCoordinatesBecauseTheyCannotBeRoutedTo() {
        server.expect(once(), requestTo(
            "https://restapi.amap.test/v3/assistant/inputtips?key=secret-key&keywords=%E8%BD%AF%E4%BB%B6%E5%9B%AD&datatype=poi&city=0592&citylimit=true&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","tips":[
                  {"name":"软件园三期","location":"118.178100,24.487900","adcode":"350211","id":"B001","district":"福建省厦门市集美区","address":"observation"},
                  {"name":"软件园(行政区)","location":[],"adcode":"350211","id":"B002"},
                  {"name":"软件园二期","location":"118.150000,24.490000","adcode":"350203","id":"B003","district":"福建省厦门市思明区","address":[]}
                ]}
                """, MediaType.APPLICATION_JSON));

        List<LocationRef> tips = provider().suggest(PlaceQuery.of("软件园", "0592", null, 10));

        assertThat(tips).hasSize(2);
        assertThat(tips).extracting(LocationRef::displayName)
            .containsExactly("软件园三期", "软件园二期");
        assertThat(tips.getFirst().providerPlaceId()).isEqualTo("B001");
        // AMap returns [] rather than "" for a missing address; that must not become "[]" text.
        assertThat(tips.getLast().formattedAddress()).isEqualTo("福建省厦门市思明区");
        server.verify();
    }

    @Test
    void searchPoiReturnsStructuredResults() {
        server.expect(once(), requestTo(
            "https://restapi.amap.test/v5/place/text?key=secret-key&keywords=%E5%A4%A7%E5%AD%A6&page_size=10&page_num=1&region=0592&city_limit=true&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","pois":[
                  {"name":"集美大学","location":"118.097200,24.575100","adcode":"350211","citycode":"0592","id":"P001","address":"集美区银江路185号"}
                ]}
                """, MediaType.APPLICATION_JSON));

        List<LocationRef> pois = provider().searchPoi(PlaceQuery.of("大学", "0592", null, 10));

        assertThat(pois).hasSize(1);
        assertThat(pois.getFirst().displayName()).isEqualTo("集美大学");
        assertThat(pois.getFirst().source()).isEqualTo(LocationSource.POI_SEARCH);
        assertThat(pois.getFirst().point().latitude()).isEqualTo(24.5751);
        server.verify();
    }

    @Test
    void failsClosedWhenApiKeyIsMissingRatherThanReturningDemoData() {
        properties.setApiKey("");

        assertThatThrownBy(() -> provider().quote(RouteQuoteRequest.ofText("A", "B", "厦门")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("AMAP_API_KEY is not configured");
        assertThatThrownBy(() -> provider().reverseGeocode(GeoPoint.gcj02(24.48, 118.17)))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> provider().suggest(PlaceQuery.of("软件园", null, null, 5)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void surfacesProviderQuotaAndInvalidKeyErrorsInsteadOfDegrading() {
        server.expect(once(), requestTo(
            "https://restapi.amap.test/v3/geocode/regeo?key=secret-key&location=118.1781,24.4879&extensions=base&output=json"))
            .andRespond(withSuccess("""
                {"status":"0","info":"DAILY_QUERY_OVER_LIMIT","infocode":"10003"}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().reverseGeocode(GeoPoint.gcj02(24.4879, 118.1781)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("DAILY_QUERY_OVER_LIMIT");
        server.verify();
    }

    @Test
    void classifiesInvalidProviderParametersAsANonTransientRequestFailure() {
        server.expect(once(), requestTo(
            "https://restapi.amap.test/v3/geocode/regeo?key=secret-key&location=118.1781,24.4879&extensions=base&output=json"))
            .andRespond(withSuccess("""
                {"status":"0","info":"INVALID_PARAMS","infocode":"20000"}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().reverseGeocode(GeoPoint.gcj02(24.4879, 118.1781)))
            .isInstanceOf(MapProviderRequestException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo("MAP_REQUEST_INVALID"));
        server.verify();
    }

    @Test
    void rejectsAReverseGeocodeResponseWithoutAnAdcodeBecauseMatchingNeedsOne() {
        server.expect(once(), requestTo(
            "https://restapi.amap.test/v3/geocode/regeo?key=secret-key&location=118.1781,24.4879&extensions=base&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","regeocode":{"formatted_address":"某地","addressComponent":{}}}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider().reverseGeocode(GeoPoint.gcj02(24.4879, 118.1781)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("adcode");
        server.verify();
    }

    private static LocationRef resolved(String name, double lat, double lng, String adcode) {
        return new LocationRef(
            GeoPoint.gcj02(lat, lng), "amap", "P-" + adcode, "0592", adcode, name, name,
            LocationSource.AUTOCOMPLETE, null, java.time.Instant.now());
    }
}
