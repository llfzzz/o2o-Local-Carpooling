package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class InternalMapControllerTest {

    private final DemoMapProvider demoProvider = new DemoMapProvider();

    private InternalMapController controllerFor(String mapType) {
        ProviderProperties providers = new ProviderProperties();
        providers.getMap().setType(mapType);
        MapProviderSelector selector = new MapProviderSelector(List.of(demoProvider), providers);
        return new InternalMapController(selector, demoProvider);
    }

    @Test
    void demoPlacesReturnsCityFixturesWhenDemoProviderIsActive() {
        List<LocationRef> places = controllerFor("demo").demoPlaces("0592");

        assertThat(places).isNotEmpty();
        // Filtered to the requested city.
        assertThat(places).allSatisfy(place -> assertThat(place.cityCode()).isEqualTo("0592"));
        // Enough distinct places to build a random route.
        assertThat(places.stream().map(LocationRef::displayName).distinct().count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void demoPlacesIs404WhenTheActiveProviderIsNotDemo() {
        // Random generation against a real provider is meaningless and would burn quota, so the
        // endpoint does not exist there — 404, indistinguishable from "no such path".
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> controllerFor("amap").demoPlaces("0592"))
            .satisfies(exception -> {
                assertThat(exception.errorCode()).isEqualTo("MAP_DEMO_PLACES_UNAVAILABLE");
                assertThat(exception.status().value()).isEqualTo(404);
            });
    }
}
