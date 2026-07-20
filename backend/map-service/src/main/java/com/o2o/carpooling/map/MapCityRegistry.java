package com.o2o.carpooling.map;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The supported-city allowlist, keyed by administrative area code (adcode).
 *
 * <p>City support is <em>configuration</em>, never a conditional in code — that is what makes the
 * product work in arbitrary cities rather than the one it was demoed in. An empty allowlist means
 * "no restriction", which is what local development and the demo profile use.
 *
 * <p>An adcode identifies a district (e.g. 350211 集美区); the allowlist may name either a district
 * or its parent city prefix, so configuring {@code 3502} enables all of 厦门.
 */
@Component
@ConfigurationProperties(prefix = "map.cities")
public class MapCityRegistry {

    private List<SupportedCity> enabled = new ArrayList<>();

    public List<SupportedCity> getEnabled() {
        return enabled;
    }

    public void setEnabled(List<SupportedCity> enabled) {
        this.enabled = enabled == null ? new ArrayList<>() : enabled;
    }

    /** True when no allowlist is configured, i.e. every city is permitted. */
    boolean isUnrestricted() {
        return enabled.isEmpty();
    }

    boolean isEnabled(String adcode) {
        if (isUnrestricted()) {
            return true;
        }
        if (!StringUtils.hasText(adcode)) {
            return false;
        }
        return enabled.stream().anyMatch(city -> adcode.startsWith(city.getAdcodePrefix()));
    }

    void requireEnabled(String adcode) {
        if (!isEnabled(adcode)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MAP_CITY_NOT_SUPPORTED",
                "service is not available in this area yet");
        }
    }

    /** The allowlist as the client needs it: adcode, display name and city code, in config order. */
    List<Map<String, String>> describe() {
        List<Map<String, String>> described = new ArrayList<>();
        for (SupportedCity city : enabled) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("adcodePrefix", city.getAdcodePrefix());
            entry.put("name", city.getName());
            entry.put("cityCode", city.getCityCode());
            described.add(entry);
        }
        return described;
    }

    public static class SupportedCity {

        /** Adcode or adcode prefix; a prefix enables every district beneath it. */
        private String adcodePrefix = "";

        /** Localized display name shown in the city picker. */
        private String name = "";

        /** Provider city code, used to bias searches. */
        private String cityCode = "";

        public String getAdcodePrefix() {
            return adcodePrefix;
        }

        public void setAdcodePrefix(String adcodePrefix) {
            this.adcodePrefix = adcodePrefix;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCityCode() {
            return cityCode;
        }

        public void setCityCode(String cityCode) {
            this.cityCode = cityCode;
        }
    }
}
