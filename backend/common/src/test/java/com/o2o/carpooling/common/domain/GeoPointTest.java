package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoPointTest {

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> GeoPoint.gcj02(91.0, 118.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("latitude");
        assertThatThrownBy(() -> GeoPoint.gcj02(24.0, 181.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("longitude");
        assertThatThrownBy(() -> GeoPoint.gcj02(Double.NaN, 118.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingDatum() {
        assertThatThrownBy(() -> new GeoPoint(24.48, 118.17, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("datum");
    }

    @Test
    void normalizesToSevenDecimalPlacesSoDecimalColumnsRoundTrip() {
        GeoPoint point = GeoPoint.gcj02(24.4879001234567, 118.1781009876543);

        assertThat(point.latitude()).isEqualTo(24.4879001);
        assertThat(point.longitude()).isEqualTo(118.178101);
    }

    @Test
    void parsesAndRendersProviderLngLatForm() {
        GeoPoint point = GeoPoint.parseProviderLngLat("118.178100,24.487900", CoordinateDatum.GCJ02);

        assertThat(point.latitude()).isEqualTo(24.4879);
        assertThat(point.longitude()).isEqualTo(118.1781);
        assertThat(point.datum()).isEqualTo(CoordinateDatum.GCJ02);
        assertThat(point.toProviderLngLat()).isEqualTo("118.1781,24.4879");
    }

    @Test
    void rejectsMalformedProviderCoordinate() {
        assertThatThrownBy(() -> GeoPoint.parseProviderLngLat("118.1781", CoordinateDatum.GCJ02))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoPoint.parseProviderLngLat("east,north", CoordinateDatum.GCJ02))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoPoint.parseProviderLngLat("  ", CoordinateDatum.GCJ02))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void measuresDistanceBetweenTwoPointsOfTheSameDatum() {
        // 软件园三期 -> 集美大学, ~11.5km apart as the crow flies.
        GeoPoint softwarePark = GeoPoint.gcj02(24.4879, 118.1781);
        GeoPoint jimeiUniversity = GeoPoint.gcj02(24.5751, 118.0972);

        assertThat(softwarePark.distanceMetersTo(jimeiUniversity)).isBetween(11_000.0, 13_000.0);
    }

    @Test
    void refusesToMeasureDistanceAcrossDatums() {
        assertThatThrownBy(() -> GeoPoint.gcj02(24.48, 118.17)
            .distanceMetersTo(GeoPoint.wgs84(24.57, 118.09)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GCJ02");
    }
}
