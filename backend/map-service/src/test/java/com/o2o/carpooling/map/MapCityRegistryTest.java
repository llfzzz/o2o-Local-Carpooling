package com.o2o.carpooling.map;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapCityRegistryTest {

    @Test
    void anEmptyAllowlistPermitsEveryCity() {
        MapCityRegistry registry = new MapCityRegistry();

        assertThat(registry.isUnrestricted()).isTrue();
        assertThat(registry.isEnabled("350211")).isTrue();
        assertThat(registry.isEnabled("110105")).isTrue();
        assertThatCode(() -> registry.requireEnabled("510107")).doesNotThrowAnyException();
    }

    @Test
    void aPrefixEnablesEveryDistrictBeneathIt() {
        MapCityRegistry registry = registry(city("3502", "厦门", "0592"));

        assertThat(registry.isEnabled("350203")).isTrue();  // 思明区
        assertThat(registry.isEnabled("350211")).isTrue();  // 集美区
        assertThat(registry.isEnabled("350206")).isTrue();  // 湖里区
        assertThat(registry.isEnabled("350100")).isFalse(); // 福州, same province
    }

    @Test
    void servesSeveralUnrelatedCitiesFromConfigurationAlone() {
        MapCityRegistry registry = registry(
            city("3502", "厦门", "0592"),
            city("1101", "北京", "010"),
            city("5101", "成都", "028"));

        assertThat(registry.isEnabled("350211")).isTrue();
        assertThat(registry.isEnabled("110108")).isTrue();
        assertThat(registry.isEnabled("510104")).isTrue();
        assertThat(registry.isEnabled("230103")).isFalse(); // 哈尔滨 not configured
    }

    @Test
    void rejectsUnsupportedAreasWithAStableErrorCode() {
        MapCityRegistry registry = registry(city("3502", "厦门", "0592"));

        assertThatThrownBy(() -> registry.requireEnabled("510104"))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("MAP_CITY_NOT_SUPPORTED"));
    }

    @Test
    void treatsAMissingAdcodeAsUnsupportedOnceAnAllowlistExists() {
        MapCityRegistry registry = registry(city("3502", "厦门", "0592"));

        assertThat(registry.isEnabled(null)).isFalse();
        assertThat(registry.isEnabled("  ")).isFalse();
    }

    @Test
    void describesTheAllowlistInConfigurationOrderForTheCityPicker() {
        MapCityRegistry registry = registry(city("3502", "厦门", "0592"), city("1101", "北京", "010"));

        assertThat(registry.describe()).hasSize(2);
        assertThat(registry.describe().getFirst())
            .containsEntry("adcodePrefix", "3502")
            .containsEntry("name", "厦门")
            .containsEntry("cityCode", "0592");
        assertThat(registry.describe().get(1)).containsEntry("name", "北京");
    }

    @Test
    void toleratesANullAllowlistFromConfiguration() {
        MapCityRegistry registry = new MapCityRegistry();
        registry.setEnabled(null);

        assertThat(registry.isUnrestricted()).isTrue();
    }

    private MapCityRegistry registry(MapCityRegistry.SupportedCity... cities) {
        MapCityRegistry registry = new MapCityRegistry();
        registry.setEnabled(new ArrayList<>(List.of(cities)));
        return registry;
    }

    private MapCityRegistry.SupportedCity city(String adcodePrefix, String name, String cityCode) {
        MapCityRegistry.SupportedCity city = new MapCityRegistry.SupportedCity();
        city.setAdcodePrefix(adcodePrefix);
        city.setName(name);
        city.setCityCode(cityCode);
        return city;
    }
}
