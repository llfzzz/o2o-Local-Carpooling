package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.GeoPoint;

/**
 * A geographic keyword lookup. Used for both autocomplete (input tips) and full POI search.
 *
 * @param keyword  partial or colloquial place name the user typed
 * @param cityCode provider city code narrowing the search; null searches nationwide
 * @param bias     optional point to rank results around (the user's current position)
 * @param size     maximum results to return
 */
record PlaceQuery(String keyword, String cityCode, GeoPoint bias, int size) {

    static final int DEFAULT_SIZE = 10;
    static final int MAX_SIZE = 25;

    PlaceQuery {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword is required");
        }
        keyword = keyword.trim();
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    static PlaceQuery of(String keyword, String cityCode, GeoPoint bias, Integer size) {
        return new PlaceQuery(keyword, cityCode, bias, size == null ? DEFAULT_SIZE : size);
    }
}
