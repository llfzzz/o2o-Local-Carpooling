package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinateTransformTest {

    @Test
    void shiftsWgs84IntoGcj02ByRoughlyFiveHundredMetres() {
        GeoPoint gps = GeoPoint.wgs84(24.4879, 118.1781);

        GeoPoint amap = CoordinateTransform.wgs84ToGcj02(gps);

        assertThat(amap.datum()).isEqualTo(CoordinateDatum.GCJ02);
        // The whole reason this class exists: untransformed coordinates land streets away.
        double shift = Haversine.distanceMeters(gps.latitude(), gps.longitude(), amap.latitude(), amap.longitude());
        assertThat(shift).isBetween(300.0, 800.0);
    }

    @Test
    void inverseTransformRecoversTheOriginalWithinAMetre() {
        GeoPoint gps = GeoPoint.wgs84(24.4879, 118.1781);

        GeoPoint recovered = CoordinateTransform.gcj02ToWgs84(CoordinateTransform.wgs84ToGcj02(gps));

        double error = Haversine.distanceMeters(
            gps.latitude(), gps.longitude(), recovered.latitude(), recovered.longitude());
        assertThat(error).isLessThan(1.0);
    }

    @Test
    void roundTripsAcrossUnrelatedChineseCities() {
        // Multi-city coverage: nothing here may be tuned to one region.
        GeoPoint[] cities = {
            GeoPoint.wgs84(39.9042, 116.4074),  // 北京
            GeoPoint.wgs84(31.2304, 121.4737),  // 上海
            GeoPoint.wgs84(23.1291, 113.2644),  // 广州
            GeoPoint.wgs84(30.5728, 104.0668),  // 成都
            GeoPoint.wgs84(45.8038, 126.5349)   // 哈尔滨
        };

        for (GeoPoint city : cities) {
            GeoPoint recovered = CoordinateTransform.gcj02ToWgs84(CoordinateTransform.wgs84ToGcj02(city));
            double error = Haversine.distanceMeters(
                city.latitude(), city.longitude(), recovered.latitude(), recovered.longitude());
            assertThat(error).as("round-trip error at %s", city).isLessThan(1.0);
        }
    }

    @Test
    void leavesCoordinatesOutsideChinaUntouched() {
        GeoPoint tokyo = GeoPoint.wgs84(35.6762, 139.6503);

        GeoPoint converted = CoordinateTransform.wgs84ToGcj02(tokyo);

        assertThat(converted.latitude()).isEqualTo(tokyo.latitude());
        assertThat(converted.longitude()).isEqualTo(tokyo.longitude());
        assertThat(converted.datum()).isEqualTo(CoordinateDatum.GCJ02);
    }

    @Test
    void toDatumIsANoOpWhenAlreadyInTheTargetDatum() {
        GeoPoint amap = GeoPoint.gcj02(24.4879, 118.1781);

        assertThat(CoordinateTransform.toDatum(amap, CoordinateDatum.GCJ02)).isSameAs(amap);
    }

    @Test
    void toDatumConvertsInBothDirections() {
        GeoPoint gps = GeoPoint.wgs84(24.4879, 118.1781);

        GeoPoint amap = CoordinateTransform.toDatum(gps, CoordinateDatum.GCJ02);
        assertThat(amap.datum()).isEqualTo(CoordinateDatum.GCJ02);

        GeoPoint back = CoordinateTransform.toDatum(amap, CoordinateDatum.WGS84);
        assertThat(back.datum()).isEqualTo(CoordinateDatum.WGS84);
    }

    @Test
    void rejectsAWronglyLabelledInput() {
        assertThatThrownBy(() -> CoordinateTransform.wgs84ToGcj02(GeoPoint.gcj02(24.48, 118.17)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expected WGS84");
    }
}
