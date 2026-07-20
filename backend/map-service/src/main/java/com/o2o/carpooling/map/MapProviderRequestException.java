package com.o2o.carpooling.map;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;

/** A non-transient provider rejection caused by the request itself; stale data must not hide it. */
final class MapProviderRequestException extends BusinessException {

    MapProviderRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "MAP_REQUEST_INVALID", message);
    }
}
