package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationRefTest {

    private static LocationRef amapPlace(String adcode, String displayName) {
        return new LocationRef(
            GeoPoint.gcj02(24.4879, 118.1781),
            "amap",
            "B0FFH5V8N9",
            "0592",
            adcode,
            displayName,
            "福建省厦门市思明区" + displayName,
            LocationSource.AUTOCOMPLETE,
            35,
            Instant.parse("2026-07-20T02:00:00Z")
        );
    }

    @Test
    void requiresTheFieldsMatchingDependsOn() {
        assertThatThrownBy(() -> amapPlaceWith(null, "软件园三期"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("adcode");
        assertThatThrownBy(() -> amapPlaceWith("350203", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("displayName");
    }

    @Test
    void requiresAProviderSoAResultCanAlwaysBeAttributed() {
        assertThatThrownBy(() -> new LocationRef(
            GeoPoint.gcj02(24.48, 118.17), "  ", null, "0592", "350203", "某处", null,
            LocationSource.MAP_PIN, null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider");
    }

    @Test
    void allowsANullPlaceIdBecauseADraggedPinNamesNoPoi() {
        LocationRef pin = new LocationRef(
            GeoPoint.gcj02(24.4879, 118.1781), "amap", null, "0592", "350203",
            "思明区软件园三期附近", "福建省厦门市思明区", LocationSource.MAP_PIN, null, Instant.now());

        assertThat(pin.providerPlaceId()).isNull();
        assertThat(pin.isDemo()).isFalse();
    }

    @Test
    void flagsDemoOriginSoTheUiCanNeverPassItOffAsReal() {
        LocationRef demoProvider = new LocationRef(
            GeoPoint.gcj02(24.4879, 118.1781), "demo", null, "0592", "350203",
            "演示地点", "演示地址", LocationSource.AUTOCOMPLETE, null, Instant.now());
        LocationRef demoSeed = new LocationRef(
            GeoPoint.gcj02(24.4879, 118.1781), "amap", null, "0592", "350203",
            "种子地点", "种子地址", LocationSource.DEMO_SEED, null, Instant.now());

        assertThat(demoProvider.isDemo()).isTrue();
        assertThat(demoSeed.isDemo()).isTrue();
        assertThat(amapPlace("350203", "软件园三期").isDemo()).isFalse();
    }

    @Test
    void comparesCitiesByAdcodeNotByDisplayText() {
        LocationRef siming = amapPlace("350203", "软件园三期");
        LocationRef jimei = amapPlace("350211", "集美大学");
        LocationRef otherSiming = amapPlace("350203", "厦门北站");

        assertThat(siming.sameCityAs(otherSiming)).isTrue();
        assertThat(siming.sameCityAs(jimei)).isFalse();
        assertThat(siming.sameCityAs(null)).isFalse();
    }

    private static LocationRef amapPlaceWith(String adcode, String displayName) {
        return new LocationRef(
            GeoPoint.gcj02(24.4879, 118.1781), "amap", null, "0592", adcode, displayName,
            "地址", LocationSource.AUTOCOMPLETE, null, Instant.now());
    }
}
