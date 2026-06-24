package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapRouteProviderTest {

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

    @Test
    void geocodesTextAddressesAndParsesDrivingRoute() {
        server.expect(once(), requestTo("https://restapi.amap.test/v3/geocode/geo?key=secret-key&address=%E8%BD%AF%E4%BB%B6%E5%9B%AD%E4%B8%89%E6%9C%9F&city=%E5%8E%A6%E9%97%A8&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","geocodes":[{"location":"118.178100,24.487900"}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://restapi.amap.test/v3/geocode/geo?key=secret-key&address=%E9%9B%86%E7%BE%8E%E5%A4%A7%E5%AD%A6&city=%E5%8E%A6%E9%97%A8&output=json"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","geocodes":[{"location":"118.097200,24.575100"}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://restapi.amap.test/v5/direction/driving?key=secret-key&origin=118.178100,24.487900&destination=118.097200,24.575100&show_fields=cost&output=json"))
            .andExpect(queryParam("key", "secret-key"))
            .andRespond(withSuccess("""
                {"status":"1","info":"OK","infocode":"10000","route":{"paths":[{"distance":"22500","cost":{"duration":"2600"}}]}}
                """, MediaType.APPLICATION_JSON));

        AmapRouteProvider provider = new AmapRouteProvider(properties, restClientBuilder);
        RouteQuoteResult result = provider.quote(new RouteQuoteRequest("软件园三期", "集美大学", "厦门"));
        RouteSnapshot route = result.routeSnapshot();

        assertThat(route.distanceMeters()).isEqualTo(22_500);
        assertThat(route.durationSeconds()).isEqualTo(2_600);
        assertThat(route.providerTrace()).isEqualTo("amap-v5");
        assertThat(result.originCoordinate()).isEqualTo("118.178100,24.487900");
        assertThat(result.destinationCoordinate()).isEqualTo("118.097200,24.575100");
        assertThat(result.providerResponseSnapshot()).doesNotContain("secret-key");
        server.verify();
    }
}
