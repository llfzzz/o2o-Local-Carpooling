package com.o2o.carpooling.map;

import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;

import java.util.List;

/**
 * Provider seam for every map capability the product needs.
 *
 * <p>The provider is selected explicitly via {@code providers.map.type} (demo → {@link DemoMapProvider},
 * amap → {@link AmapMapProvider}). A configured real provider that fails is never silently downgraded
 * to the demo one — it fails closed, so a fabricated route can never be mistaken for a real one.
 *
 * <p>Every method that returns a place returns a {@link LocationRef}: coordinates, datum, administrative
 * area and provider identity together. Free-form text is an input to resolution, never a result.
 */
interface MapProvider {

    /** Provider key, matched against {@code providers.map.type}. */
    String name();

    /** Distance, duration and geometry between two points. */
    RouteQuoteResult quote(RouteQuoteRequest request);

    /** Coordinates → structured place. Used for "use my location" and for dragged map pins. */
    LocationRef reverseGeocode(GeoPoint point);

    /** Keyword autocomplete for as-you-type input. Cheaper and faster than {@link #searchPoi}. */
    List<LocationRef> suggest(PlaceQuery query);

    /** Full POI search, returning richer results than {@link #suggest}. */
    List<LocationRef> searchPoi(PlaceQuery query);
}
