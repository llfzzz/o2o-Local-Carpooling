package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.GeoMatchingPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Matching and pricing knobs.
 *
 * <p>These are configuration rather than constants because the right values differ by market: a
 * city with sparse driver coverage needs wider radii than a dense one, and fares differ per region.
 * Pricing in particular used to be a hardcoded field initializer inside the repository.
 */
@Component
@ConfigurationProperties(prefix = "trip")
public class TripMatchingProperties {

    private final Matching matching = new Matching();
    private final Pricing pricing = new Pricing();
    private final Tracking tracking = new Tracking();

    public Matching getMatching() {
        return matching;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public Tracking getTracking() {
        return tracking;
    }

    GeoMatchingPolicy toPolicy() {
        return new GeoMatchingPolicy(
            matching.getOriginRadiusMeters(),
            matching.getDestinationRadiusMeters(),
            matching.getDepartureWindow(),
            matching.getMaxResults()
        );
    }

    public static class Matching {

        /** How far a trip's start may be from the rider's start. */
        private int originRadiusMeters = 3_000;

        /** How far a trip's end may be from the rider's end; looser than pickup. */
        private int destinationRadiusMeters = 5_000;

        /** Symmetric tolerance around the rider's target departure time. */
        private Duration departureWindow = Duration.ofHours(2);

        private int maxResults = 50;

        public int getOriginRadiusMeters() {
            return originRadiusMeters;
        }

        public void setOriginRadiusMeters(int originRadiusMeters) {
            this.originRadiusMeters = originRadiusMeters;
        }

        public int getDestinationRadiusMeters() {
            return destinationRadiusMeters;
        }

        public void setDestinationRadiusMeters(int destinationRadiusMeters) {
            this.destinationRadiusMeters = destinationRadiusMeters;
        }

        public Duration getDepartureWindow() {
            return departureWindow;
        }

        public void setDepartureWindow(Duration departureWindow) {
            this.departureWindow = departureWindow;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }

    /** Live pickup tracking. Positions are ephemeral; nothing here implies durable storage. */
    public static class Tracking {

        /**
         * How long a reported position stays live. Doubles as the retention limit — a driver's
         * whereabouts never outlive it — and as the offline signal, since an entry that is not
         * refreshed simply expires.
         */
        private Duration presenceTtl = Duration.ofSeconds(45);

        /** Rate-limit budget per trip, roughly 1 update per 5s at the default 10s cadence. */
        private int maxUpdates = 12;

        private Duration updateWindow = Duration.ofSeconds(60);

        /** How often the SSE stream re-reads the current position. */
        private Duration streamInterval = Duration.ofSeconds(3);

        /** Safety cap so a forgotten tab cannot hold a connection open indefinitely. */
        private Duration streamMaxDuration = Duration.ofMinutes(30);

        public Duration getPresenceTtl() {
            return presenceTtl;
        }

        public void setPresenceTtl(Duration presenceTtl) {
            this.presenceTtl = presenceTtl;
        }

        public int getMaxUpdates() {
            return maxUpdates;
        }

        public void setMaxUpdates(int maxUpdates) {
            this.maxUpdates = maxUpdates;
        }

        public Duration getUpdateWindow() {
            return updateWindow;
        }

        public void setUpdateWindow(Duration updateWindow) {
            this.updateWindow = updateWindow;
        }

        public Duration getStreamInterval() {
            return streamInterval;
        }

        public void setStreamInterval(Duration streamInterval) {
            this.streamInterval = streamInterval;
        }

        public Duration getStreamMaxDuration() {
            return streamMaxDuration;
        }

        public void setStreamMaxDuration(Duration streamMaxDuration) {
            this.streamMaxDuration = streamMaxDuration;
        }
    }

    public static class Pricing {

        private BigDecimal baseFare = new BigDecimal("6.00");
        private BigDecimal perKmFare = new BigDecimal("1.20");

        public BigDecimal getBaseFare() {
            return baseFare;
        }

        public void setBaseFare(BigDecimal baseFare) {
            this.baseFare = baseFare;
        }

        public BigDecimal getPerKmFare() {
            return perKmFare;
        }

        public void setPerKmFare(BigDecimal perKmFare) {
            this.perKmFare = perKmFare;
        }
    }
}
