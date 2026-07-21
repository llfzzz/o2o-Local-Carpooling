package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service map lookups (NOT under /api/**, so the Gateway never routes them).
 *
 * <p>Demo places back trip-service's "random route" virtual-trip feature. This exists only when
 * the demo map provider is active: 404 otherwise, because picking two curated fixture places is
 * meaningless against a real provider and would waste quota.
 */
@RestController
@RequestMapping("/internal/maps")
class InternalMapController {

    private final MapProviderSelector selector;
    private final DemoMapProvider demoMapProvider;

    InternalMapController(MapProviderSelector selector, DemoMapProvider demoMapProvider) {
        this.selector = selector;
        this.demoMapProvider = demoMapProvider;
    }

    @GetMapping("/demo-places")
    List<LocationRef> demoPlaces(@RequestParam(required = false) String cityCode) {
        if (!selector.isDemoActive()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "MAP_DEMO_PLACES_UNAVAILABLE", "not found");
        }
        return demoMapProvider.demoPlaces(cityCode);
    }
}
