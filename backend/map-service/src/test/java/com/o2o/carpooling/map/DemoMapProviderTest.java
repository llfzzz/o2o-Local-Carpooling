package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoMapProviderTest {

    private final DemoMapProvider provider = new DemoMapProvider();

    @Test
    void labelsEveryResultAsDemoSoItCanNeverPassForRealProviderOutput() {
        List<LocationRef> tips = provider.suggest(PlaceQuery.of("软件园", null, null, 10));

        assertThat(tips).isNotEmpty();
        assertThat(tips).allSatisfy(tip -> {
            assertThat(tip.provider()).isEqualTo("demo");
            assertThat(tip.isDemo()).isTrue();
        });
    }

    @Test
    void quotesRoutesInSeveralUnrelatedCitiesNotJustTheDemoRoute() {
        // The whole point of the fixture set: nothing may be tuned to one city.
        assertThat(quoteBetween("软件园三期", "集美大学").distanceMeters()).isPositive();
        assertThat(quoteBetween("中关村", "北京南站").distanceMeters()).isPositive();
        assertThat(quoteBetween("天府广场", "成都东站").distanceMeters()).isPositive();
        assertThat(quoteBetween("哈尔滨西站", "中央大街").distanceMeters()).isPositive();
    }

    @Test
    void quoteIsDeterministicAndProportionalToRealDistance() {
        RouteSnapshot shortHop = quoteBetween("天府广场", "春熙路");
        RouteSnapshot longHaul = quoteBetween("中关村", "首都国际机场");

        assertThat(shortHop.distanceMeters()).isLessThan(longHaul.distanceMeters());
        // Same inputs, same numbers — a demo must be reproducible.
        assertThat(quoteBetween("天府广场", "春熙路").distanceMeters()).isEqualTo(shortHop.distanceMeters());
        assertThat(shortHop.providerTrace()).isEqualTo("amap-mock");
        assertThat(shortHop.durationSeconds()).isPositive();
    }

    @Test
    void appliesARoadFactorSoTheEstimateExceedsStraightLineDistance() {
        GeoPoint softwarePark = GeoPoint.gcj02(24.4879, 118.1781);
        GeoPoint jimei = GeoPoint.gcj02(24.5751, 118.0972);
        double straightLine = softwarePark.distanceMetersTo(jimei);

        RouteSnapshot route = quoteBetween("软件园三期", "集美大学");

        assertThat(route.distanceMeters()).isGreaterThan((int) straightLine);
        assertThat(route.distanceMeters()).isLessThan((int) (straightLine * 2));
    }

    @Test
    void carriesGeometryAndStructuredEndpointsLikeTheRealProvider() {
        RouteSnapshot route = quoteBetween("软件园三期", "集美大学");

        assertThat(route.hasGeometry()).isTrue();
        assertThat(route.origin().adcode()).isEqualTo("350211");
        assertThat(route.destination().displayName()).isEqualTo("集美大学");
    }

    @Test
    void narrowsSuggestionsToTheRequestedCity() {
        List<LocationRef> beijingOnly = provider.suggest(PlaceQuery.of("站", "010", null, 10));

        assertThat(beijingOnly).isNotEmpty();
        assertThat(beijingOnly).allSatisfy(tip -> assertThat(tip.cityCode()).isEqualTo("010"));
        assertThat(beijingOnly).extracting(LocationRef::displayName).contains("北京南站");
    }

    @Test
    void ranksSuggestionsNearestToTheBiasPointFirst() {
        // "站" matches stations in four cities; biasing to Chengdu should surface 成都东站 first.
        List<LocationRef> nearChengdu = provider.suggest(
            PlaceQuery.of("站", null, GeoPoint.gcj02(30.6570, 104.0657), 10));

        assertThat(nearChengdu.getFirst().displayName()).isEqualTo("成都东站");
    }

    @Test
    void reverseGeocodeReportsTheCallersOwnPointNotTheNearestFixture() {
        GeoPoint pin = GeoPoint.gcj02(24.4900, 118.1800);

        LocationRef place = provider.reverseGeocode(pin);

        assertThat(place.point()).isEqualTo(pin);
        assertThat(place.adcode()).isEqualTo("350211");
        assertThat(place.source()).isEqualTo(LocationSource.MAP_PIN);
        assertThat(place.displayName()).contains("软件园三期");
    }

    @Test
    void refusesUnknownTextRatherThanInventingAPlace() {
        assertThatThrownBy(() -> quoteBetween("不存在的地方", "集美大学"))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("MAP_DEMO_LOCATION_UNKNOWN"));
    }

    @Test
    void stillHonoursTheLegacyRawCoordinateForm() {
        RouteQuoteResult result = provider.quote(
            RouteQuoteRequest.ofText("118.178100,24.487900", "118.097200,24.575100", "厦门"));

        assertThat(result.routeSnapshot().distanceMeters()).isPositive();
        assertThat(result.routeSnapshot().origin().point().latitude()).isEqualTo(24.4879);
    }

    private RouteSnapshot quoteBetween(String origin, String destination) {
        return provider.quote(RouteQuoteRequest.ofText(origin, destination, null)).routeSnapshot();
    }
}
