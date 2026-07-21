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
    private final Reminder reminder = new Reminder();

    public Matching getMatching() {
        return matching;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public Tracking getTracking() {
        return tracking;
    }

    public Reminder getReminder() {
        return reminder;
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

    /** Departure reminders (trip.reminder.*): one notice per trip shortly before departure. */
    public static class Reminder {

        /** How long before departure_at the reminder fires. */
        private Duration lead = Duration.ofMinutes(30);

        /** Max trips handled per scan pass. */
        private int batchSize = 50;

        public Duration getLead() {
            return lead;
        }

        public void setLead(Duration lead) {
            this.lead = lead;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
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

    /**
     * Distance-based pricing (trip.pricing.*): the base fare covers the first
     * {@code includedKm} kilometers; only distance beyond it is charged at {@code perKmFare};
     * {@code minFare} floors the result. All BigDecimal — never floating point.
     */
    public static class Pricing {

        private BigDecimal baseFare = new BigDecimal("6.00");
        private BigDecimal includedKm = new BigDecimal("3.0");
        private BigDecimal perKmFare = new BigDecimal("1.20");
        private BigDecimal minFare = new BigDecimal("6.00");
        private String currency = "CNY";

        public BigDecimal getBaseFare() {
            return baseFare;
        }

        public void setBaseFare(BigDecimal baseFare) {
            this.baseFare = baseFare;
        }

        public BigDecimal getIncludedKm() {
            return includedKm;
        }

        public void setIncludedKm(BigDecimal includedKm) {
            this.includedKm = includedKm;
        }

        public BigDecimal getMinFare() {
            return minFare;
        }

        public void setMinFare(BigDecimal minFare) {
            this.minFare = minFare;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public BigDecimal getPerKmFare() {
            return perKmFare;
        }

        public void setPerKmFare(BigDecimal perKmFare) {
            this.perKmFare = perKmFare;
        }
    }
}
