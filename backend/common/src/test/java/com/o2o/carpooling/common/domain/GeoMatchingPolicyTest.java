package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoMatchingPolicyTest {

    private final GeoMatchingPolicy policy =
        new GeoMatchingPolicy(3_000, 5_000, Duration.ofHours(2), 50);

    private static final Instant NOON = Instant.parse("2026-07-20T04:00:00Z");

    @Test
    void rejectsNonsensicalConfiguration() {
        assertThatThrownBy(() -> new GeoMatchingPolicy(0, 5_000, Duration.ofHours(2), 50))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoMatchingPolicy(3_000, 5_000, Duration.ZERO, 50))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoMatchingPolicy(3_000, 5_000, Duration.ofHours(2), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesSeparateRadiiToOriginAndDestination() {
        // Pickup must be close; drop-off may be looser.
        assertThat(policy.originWithinRadius(2_999)).isTrue();
        assertThat(policy.originWithinRadius(3_001)).isFalse();
        assertThat(policy.destinationWithinRadius(4_999)).isTrue();
        assertThat(policy.destinationWithinRadius(5_001)).isFalse();
    }

    @Test
    void treatsTheDepartureWindowAsSymmetricAndInclusive() {
        assertThat(policy.departureWithinWindow(NOON, NOON.plus(Duration.ofHours(2)))).isTrue();
        assertThat(policy.departureWithinWindow(NOON, NOON.minus(Duration.ofHours(2)))).isTrue();
        assertThat(policy.departureWithinWindow(NOON, NOON.plus(Duration.ofHours(2).plusSeconds(1)))).isFalse();
        assertThat(policy.departureWithinWindow(NOON, NOON.minus(Duration.ofHours(2).plusSeconds(1)))).isFalse();
    }

    @Test
    void treatsAnAbsentRiderTimeAsNoTimeConstraint() {
        assertThat(policy.departureWithinWindow(null, NOON.plusSeconds(999_999))).isTrue();
    }

    @Test
    void requiresAllThreeConditionsToMatch() {
        assertThat(policy.matches(1_000, 2_000, NOON, NOON.plusSeconds(600))).isTrue();
        assertThat(policy.matches(9_000, 2_000, NOON, NOON.plusSeconds(600))).isFalse();
        assertThat(policy.matches(1_000, 9_000, NOON, NOON.plusSeconds(600))).isFalse();
        assertThat(policy.matches(1_000, 2_000, NOON, NOON.plus(Duration.ofHours(5)))).isFalse();
    }

    @Test
    void ranksCloserTripsAhead() {
        double near = policy.score(500, 500, NOON, NOON);
        double far = policy.score(2_500, 4_000, NOON, NOON);

        assertThat(near).isLessThan(far);
    }

    @Test
    void usesTimeGapOnlyAsATieBreakerBetweenEquallyCloseTrips() {
        double sameTime = policy.score(1_000, 1_000, NOON, NOON);
        double anHourOff = policy.score(1_000, 1_000, NOON, NOON.plus(Duration.ofHours(1)));

        assertThat(sameTime).isLessThan(anHourOff);
    }

    @Test
    void letsAMuchCloserTripBeatABetterTimedDistantOne() {
        // 200m away but an hour off, versus perfectly timed but near the radius edge.
        double closeButLate = policy.score(100, 100, NOON, NOON.plus(Duration.ofHours(1)));
        double punctualButFar = policy.score(2_900, 4_800, NOON, NOON);

        assertThat(closeButLate).isLessThan(punctualButFar);
    }

    @Test
    void boundingBoxAlwaysContainsTheMatchRadius() {
        // The pre-filter box must never exclude a trip that real distance would accept.
        double latDegrees = policy.boundingBoxLatitudeDegrees();
        double edgeMeters = Haversine.distanceMeters(24.4879, 118.1781, 24.4879 + latDegrees, 118.1781);

        assertThat(edgeMeters).isGreaterThanOrEqualTo(5_000.0);
    }

    @Test
    void widensTheLongitudeBoxAtHigherLatitudes() {
        // A degree of longitude is shorter near the poles, so the box must span more of them.
        double atHarbin = policy.boundingBoxLongitudeDegrees(45.80);
        double atXiamen = policy.boundingBoxLongitudeDegrees(24.49);

        assertThat(atHarbin).isGreaterThan(atXiamen);

        double edgeMeters = Haversine.distanceMeters(45.80, 126.53, 45.80, 126.53 + atHarbin);
        assertThat(edgeMeters).isGreaterThanOrEqualTo(5_000.0);
    }

    @Test
    void survivesThePoleSingularityWithoutProducingInfinity() {
        assertThat(policy.boundingBoxLongitudeDegrees(89.999)).isEqualTo(180.0);
    }
}
