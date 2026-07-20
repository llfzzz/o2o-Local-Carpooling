package com.o2o.carpooling.map;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;

final class MapProviderConfigurationException extends BusinessException {

    MapProviderConfigurationException(String message) {
        super(HttpStatus.BAD_GATEWAY, "MAP_ROUTE_QUOTE_FAILED", message);
    }
}
