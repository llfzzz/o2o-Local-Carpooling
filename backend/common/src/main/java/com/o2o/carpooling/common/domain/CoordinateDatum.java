package com.o2o.carpooling.common.domain;

/**
 * The geodetic datum a coordinate pair is expressed in.
 *
 * <p>This project stores and transports {@link #GCJ02} everywhere, because the map provider
 * (AMap) both emits and consumes GCJ-02. The browser Geolocation API emits {@link #WGS84}, so
 * datum conversion happens at exactly one boundary — see {@link CoordinateTransform}.
 *
 * <p>A coordinate pair without a datum is meaningless: the two systems differ by roughly 500m
 * inside China. No type in this codebase may carry an unlabeled latitude/longitude pair.
 */
public enum CoordinateDatum {

    /** Raw GPS / browser Geolocation API. */
    WGS84,

    /** Chinese national surveying datum ("Mars coordinates"); what AMap speaks. */
    GCJ02
}
