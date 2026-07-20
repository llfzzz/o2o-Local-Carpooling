package com.o2o.carpooling.map;

import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the active {@link MapProvider} from {@code providers.map.type}.
 *
 * <p>Fails closed when the configured type has no adapter — matching the SMS/payment/OCR/identity
 * seams. A configured provider that fails at call time is never silently downgraded to the demo
 * one; that rule lives in each provider.
 */
@Component
class MapProviderSelector {

    private final List<MapProvider> providers;
    private final ProviderProperties providerProperties;

    MapProviderSelector(List<MapProvider> providers, ProviderProperties providerProperties) {
        this.providers = providers;
        this.providerProperties = providerProperties;
    }

    MapProvider active() {
        String type = providerProperties.getMap().getType();
        return providers.stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(type))
            .findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "MAP_PROVIDER_UNCONFIGURED",
                "no map provider configured for type '" + type + "'"));
    }

    /** True when the active provider produces demo data, so responses can be labelled as such. */
    boolean isDemoActive() {
        return "demo".equalsIgnoreCase(providerProperties.getMap().getType());
    }
}
