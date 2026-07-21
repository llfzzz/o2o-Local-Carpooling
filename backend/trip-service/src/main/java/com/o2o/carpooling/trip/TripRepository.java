package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.CoordinateDatum;
import com.o2o.carpooling.common.domain.GeoMatchingPolicy;
import com.o2o.carpooling.common.domain.GeoPoint;
import com.o2o.carpooling.common.domain.LocationRef;
import com.o2o.carpooling.common.domain.LocationSource;
import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PriceBreakdown;
import com.o2o.carpooling.common.domain.PricingPolicy;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.domain.SeatInventory;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.domain.TripSource;
import com.o2o.carpooling.common.domain.TripStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class TripRepository {

    private final JdbcClient jdbcClient;
    private final TripMatchingProperties matchingProperties;
    private final PricingPolicy pricingPolicy;

    TripRepository(JdbcClient jdbcClient, TripMatchingProperties matchingProperties) {
        this.jdbcClient = jdbcClient;
        this.matchingProperties = matchingProperties;
        this.pricingPolicy = new PricingPolicy(
            matchingProperties.getPricing().getBaseFare(),
            matchingProperties.getPricing().getIncludedKm(),
            matchingProperties.getPricing().getPerKmFare(),
            matchingProperties.getPricing().getMinFare(),
            matchingProperties.getPricing().getCurrency());
    }

    @Transactional
    TripOffer create(PublishTripCommand command, RouteSnapshot route) {
        return insert(command, route, TripSource.USER);
    }

    /** Inserts a demo-generated virtual trip (source=DEMO), bypassing publish validation nuances
     *  that assume a real driver. Callers are demo-only and already gated. */
    @Transactional
    TripOffer createDemo(PublishTripCommand command, RouteSnapshot route) {
        return insert(command, route, TripSource.DEMO);
    }

    private TripOffer insert(PublishTripCommand command, RouteSnapshot route, TripSource source) {
        validatePublish(command);
        if (route == null) {
            throw new IllegalArgumentException("route is required");
        }
        String tripId = "trip-" + UUID.randomUUID();
        // The full breakdown's components are persisted with the row so a later config change
        // can never make the displayed breakdown disagree with the stored price.
        PriceBreakdown breakdown = pricingPolicy.quoteBreakdown(route);
        Money price = breakdown.total();
        Instant now = Instant.now();
        // The route snapshot is the authority on where this trip actually starts and ends: it is
        // what the provider resolved, whereas the command's text is only what the user typed.
        LocationRef origin = route.origin() != null ? route.origin() : command.origin();
        LocationRef destination = route.destination() != null ? route.destination() : command.destination();

        jdbcClient.sql("""
            insert into trips (
              trip_id, driver_id, origin_text, destination_text, departure_at,
              route_id, distance_meters, duration_seconds, route_provider,
              seat_price_amount, seat_price_currency, total_seats, locked_seats,
              status, created_at, updated_at, version, idempotency_key,
              origin_lat, origin_lng, destination_lat, destination_lng, coordinate_datum,
              origin_adcode, destination_adcode, origin_city_code, destination_city_code,
              origin_place_id, destination_place_id, route_polyline,
              base_fare, included_km, per_km_fare, min_fare, source
            ) values (
              :tripId, :driverId, :originText, :destinationText, :departureAt,
              :routeId, :distanceMeters, :durationSeconds, :routeProvider,
              :seatPriceAmount, :seatPriceCurrency, :totalSeats, 0,
              :status, :createdAt, :updatedAt, 0, :idempotencyKey,
              :originLat, :originLng, :destinationLat, :destinationLng, :coordinateDatum,
              :originAdcode, :destinationAdcode, :originCityCode, :destinationCityCode,
              :originPlaceId, :destinationPlaceId, :routePolyline,
              :baseFare, :includedKm, :perKmFare, :minFare, :source
            )
            """)
            .param("idempotencyKey", StringUtils.hasText(command.idempotencyKey()) ? command.idempotencyKey() : null)
            .param("originLat", origin == null ? null : origin.point().latitudeDecimal())
            .param("originLng", origin == null ? null : origin.point().longitudeDecimal())
            .param("destinationLat", destination == null ? null : destination.point().latitudeDecimal())
            .param("destinationLng", destination == null ? null : destination.point().longitudeDecimal())
            .param("coordinateDatum", origin == null ? null : origin.point().datum().name())
            .param("originAdcode", origin == null ? null : origin.adcode())
            .param("destinationAdcode", destination == null ? null : destination.adcode())
            .param("originCityCode", origin == null ? null : origin.cityCode())
            .param("destinationCityCode", destination == null ? null : destination.cityCode())
            .param("originPlaceId", origin == null ? null : origin.providerPlaceId())
            .param("destinationPlaceId", destination == null ? null : destination.providerPlaceId())
            .param("routePolyline", route.polyline())
            .param("baseFare", breakdown.baseFare())
            .param("includedKm", breakdown.includedKm())
            .param("perKmFare", matchingProperties.getPricing().getPerKmFare())
            .param("minFare", matchingProperties.getPricing().getMinFare())
            .param("source", source.name())
            .param("tripId", tripId)
            .param("driverId", command.driverId())
            .param("originText", displayText(origin, command.originText()))
            .param("destinationText", displayText(destination, command.destinationText()))
            .param("departureAt", command.departureAt())
            .param("routeId", route.routeId())
            .param("distanceMeters", route.distanceMeters())
            .param("durationSeconds", route.durationSeconds())
            .param("routeProvider", route.providerTrace())
            .param("seatPriceAmount", price.amount())
            .param("seatPriceCurrency", price.currency())
            .param("totalSeats", command.totalSeats())
            .param("status", TripStatus.PUBLISHED.name())
            .param("createdAt", now)
            .param("updatedAt", now)
            .update();

        return findByTripId(tripId).orElseThrow();
    }

    Optional<TripOffer> findByTripId(String tripId) {
        return jdbcClient.sql("""
            select trip_id, driver_id, origin_text, destination_text, departure_at,
                   route_id, distance_meters, duration_seconds, route_provider,
                   seat_price_amount, seat_price_currency, total_seats, locked_seats, status,
                   origin_lat, origin_lng, destination_lat, destination_lng, coordinate_datum,
                   origin_adcode, destination_adcode, origin_city_code, destination_city_code,
                   origin_place_id, destination_place_id, route_polyline,
                   base_fare, included_km, per_km_fare, min_fare, source
            from trips
            where trip_id = :tripId
            """)
            .param("tripId", tripId)
            .query(this::mapTrip)
            .optional();
    }

    /** Returns the trip a previous publish with this key already created, if any. */
    Optional<TripOffer> findByIdempotency(String driverId, String idempotencyKey) {
        if (!StringUtils.hasText(driverId) || !StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
            select trip_id, driver_id, origin_text, destination_text, departure_at,
                   route_id, distance_meters, duration_seconds, route_provider,
                   seat_price_amount, seat_price_currency, total_seats, locked_seats, status,
                   origin_lat, origin_lng, destination_lat, destination_lng, coordinate_datum,
                   origin_adcode, destination_adcode, origin_city_code, destination_city_code,
                   origin_place_id, destination_place_id, route_polyline,
                   base_fare, included_km, per_km_fare, min_fare, source
            from trips
            where driver_id = :driverId and idempotency_key = :idempotencyKey
            """)
            .param("driverId", driverId)
            .param("idempotencyKey", idempotencyKey)
            .query(this::mapTrip)
            .optional();
    }

    /**
     * Geographic trip search: a bounding-box pre-filter in SQL (served by {@code idx_trips_geo}),
     * then exact great-circle distance and ranking in Java.
     *
     * <p>The box over-selects on purpose — a rectangle circumscribes the match circle — so the
     * precise radius check happens after loading. It replaces a {@code LIKE '%text%'} scan that
     * could not express proximity at all and defeated its own index with a leading wildcard.
     */
    List<TripOffer> searchByProximity(TripSearchQuery query) {
        GeoMatchingPolicy policy = matchingProperties.toPolicy();
        double latSpan = policy.boundingBoxLatitudeDegrees();
        double originLngSpan = policy.boundingBoxLongitudeDegrees(query.origin().latitude());
        double destinationLngSpan = policy.boundingBoxLongitudeDegrees(query.destination().latitude());

        Instant windowStart = query.departAt() == null ? null : query.departAt().minus(policy.departureWindow());
        Instant windowEnd = query.departAt() == null ? null : query.departAt().plus(policy.departureWindow());

        List<TripOffer> candidates = jdbcClient.sql("""
            select trip_id, driver_id, origin_text, destination_text, departure_at,
                   route_id, distance_meters, duration_seconds, route_provider,
                   seat_price_amount, seat_price_currency, total_seats, locked_seats, status,
                   origin_lat, origin_lng, destination_lat, destination_lng, coordinate_datum,
                   origin_adcode, destination_adcode, origin_city_code, destination_city_code,
                   origin_place_id, destination_place_id, route_polyline,
                   base_fare, included_km, per_km_fare, min_fare, source
            from trips
            where status = :status
              and origin_lat is not null and destination_lat is not null
              and (total_seats - locked_seats) >= :minSeats
              and (cast(:windowStart as timestamp) is null or departure_at >= :windowStart)
              and (cast(:windowEnd as timestamp) is null or departure_at <= :windowEnd)
              and origin_lat between :originLatMin and :originLatMax
              and origin_lng between :originLngMin and :originLngMax
              and destination_lat between :destinationLatMin and :destinationLatMax
              and destination_lng between :destinationLngMin and :destinationLngMax
            """)
            .param("status", TripStatus.PUBLISHED.name())
            .param("minSeats", query.minSeats())
            .param("windowStart", windowStart)
            .param("windowEnd", windowEnd)
            .param("originLatMin", query.origin().latitude() - latSpan)
            .param("originLatMax", query.origin().latitude() + latSpan)
            .param("originLngMin", query.origin().longitude() - originLngSpan)
            .param("originLngMax", query.origin().longitude() + originLngSpan)
            .param("destinationLatMin", query.destination().latitude() - latSpan)
            .param("destinationLatMax", query.destination().latitude() + latSpan)
            .param("destinationLngMin", query.destination().longitude() - destinationLngSpan)
            .param("destinationLngMax", query.destination().longitude() + destinationLngSpan)
            .query(this::mapTrip)
            .list();

        record Scored(TripOffer trip, double score) {
        }

        return candidates.stream()
            .map(trip -> {
                LocationRef tripOrigin = trip.route().origin();
                LocationRef tripDestination = trip.route().destination();
                if (tripOrigin == null || tripDestination == null) {
                    return null;
                }
                double originDistance = query.origin().distanceMetersTo(tripOrigin.point());
                double destinationDistance = query.destination().distanceMetersTo(tripDestination.point());
                if (!policy.matches(originDistance, destinationDistance, query.departAt(), trip.departureAt())) {
                    return null;
                }
                return new Scored(trip,
                    policy.score(originDistance, destinationDistance, query.departAt(), trip.departureAt()));
            })
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparingDouble(Scored::score))
            .limit(policy.maxResults())
            .map(Scored::trip)
            .toList();
    }

    @Transactional
    TripOffer lockSeats(String tripId, String orderId, int seats) {
        return lockSeats(tripId, orderId, seats, null);
    }

    @Transactional
    TripOffer lockSeats(String tripId, String orderId, int seats, String riderId) {
        validateSeatMutation(tripId, orderId, seats);
        Optional<SeatLock> existing = findSeatLock(orderId);
        if (existing.isPresent()) {
            SeatLock lock = existing.get();
            if (!lock.tripId().equals(tripId)) {
                throw new IllegalStateException("order " + orderId + " already locked seats for another trip");
            }
            if ("LOCKED".equals(lock.status())) {
                return requireTrip(tripId);
            }
            throw new IllegalStateException("seat lock already released for order " + orderId);
        }

        TripInventoryRow trip = requireTripForUpdate(tripId);
        if (!TripStatus.PUBLISHED.name().equals(trip.status())) {
            throw new IllegalStateException("trip " + tripId + " is not published");
        }
        if (trip.totalSeats() - trip.lockedSeats() < seats) {
            throw new IllegalStateException("not enough seats for trip " + tripId);
        }

        jdbcClient.sql("""
            update trips
            set locked_seats = locked_seats + :seats,
                updated_at = :updatedAt,
                version = version + 1
            where trip_id = :tripId
            """)
            .param("seats", seats)
            .param("updatedAt", Instant.now())
            .param("tripId", tripId)
            .update();
        jdbcClient.sql("""
            insert into trip_seat_locks (trip_id, order_id, seats, status, created_at, rider_id)
            values (:tripId, :orderId, :seats, 'LOCKED', :createdAt, :riderId)
            """)
            .param("tripId", tripId)
            .param("orderId", orderId)
            .param("seats", seats)
            .param("createdAt", Instant.now())
            .param("riderId", StringUtils.hasText(riderId) ? riderId : null)
            .update();
        return requireTrip(tripId);
    }

    @Transactional
    TripOffer releaseSeats(String tripId, String orderId) {
        if (!StringUtils.hasText(tripId)) {
            throw new IllegalArgumentException("tripId is required");
        }
        if (!StringUtils.hasText(orderId)) {
            throw new IllegalArgumentException("orderId is required");
        }

        Optional<SeatLock> existing = findSeatLock(orderId);
        if (existing.isEmpty()) {
            return requireTrip(tripId);
        }
        SeatLock lock = existing.get();
        if (!lock.tripId().equals(tripId)) {
            throw new IllegalStateException("order " + orderId + " locked seats for another trip");
        }
        if ("RELEASED".equals(lock.status())) {
            return requireTrip(tripId);
        }

        requireTripForUpdate(tripId);
        jdbcClient.sql("""
            update trips
            set locked_seats = case when locked_seats >= :seats then locked_seats - :seats else 0 end,
                updated_at = :updatedAt,
                version = version + 1
            where trip_id = :tripId
            """)
            .param("seats", lock.seats())
            .param("updatedAt", Instant.now())
            .param("tripId", tripId)
            .update();
        jdbcClient.sql("""
            update trip_seat_locks
            set status = 'RELEASED', released_at = :releasedAt
            where order_id = :orderId
            """)
            .param("releasedAt", Instant.now())
            .param("orderId", orderId)
            .update();
        return requireTrip(tripId);
    }

    /**
     * True when this rider holds a LOCKED seat on the trip — i.e. they actually booked it.
     *
     * <p>This is the gate for watching the driver's live position, which is why it checks the
     * lock status rather than merely that an order once existed: a released seat ends access.
     */
    boolean hasActiveSeatLockForRider(String tripId, String riderId) {
        if (!StringUtils.hasText(tripId) || !StringUtils.hasText(riderId)) {
            return false;
        }
        return jdbcClient.sql("""
            select count(*) from trip_seat_locks
            where trip_id = :tripId and rider_id = :riderId and status = 'LOCKED'
            """)
            .param("tripId", tripId)
            .param("riderId", riderId)
            .query(Long.class)
            .single() > 0;
    }

    /**
     * Removes future demo trips for the same endpoints so regeneration replaces rather than
     * accumulates (the provider's route_id is fresh per quote, so endpoints are the stable key).
     */
    int deleteFutureDemoTripsByEndpoints(String originText, String destinationText, Instant now) {
        return jdbcClient.sql("""
            delete from trips
            where source = 'DEMO' and origin_text = :originText
              and destination_text = :destinationText and departure_at >= :now
            """)
            .param("originText", originText)
            .param("destinationText", destinationText)
            .param("now", Timestamp.from(now))
            .update();
    }

    /** Cleanup: drop demo trips whose departure is well in the past. */
    int deleteExpiredDemoTrips(Instant before) {
        return jdbcClient.sql("""
            delete from trips where source = 'DEMO' and departure_at < :before
            """)
            .param("before", Timestamp.from(before))
            .update();
    }

    long countPublishedTrips() {
        return jdbcClient.sql("select count(*) from trips where status = :status")
            .param("status", TripStatus.PUBLISHED.name())
            .query(Long.class)
            .single();
    }

    /** Published trips departing inside [from, to] whose reminder has not fired yet. */
    List<DepartureReminderTrip> findTripsDueForDepartureReminder(Instant from, Instant to, int limit) {
        return jdbcClient.sql("""
            select trip_id, driver_id, origin_text, destination_text, departure_at
            from trips
            where status = 'PUBLISHED'
              and departure_reminder_sent_at is null
              and departure_at >= :from and departure_at <= :to
            order by departure_at asc
            limit :limit
            """)
            .param("from", Timestamp.from(from))
            .param("to", Timestamp.from(to))
            .param("limit", limit)
            .query((rs, rowNumber) -> new DepartureReminderTrip(
                rs.getString("trip_id"),
                rs.getString("driver_id"),
                rs.getString("origin_text"),
                rs.getString("destination_text"),
                rs.getTimestamp("departure_at").toInstant()
            ))
            .list();
    }

    /** Riders currently holding a LOCKED seat on the trip (pre-migration locks without rider_id are skipped). */
    List<String> listActiveSeatLockRiders(String tripId) {
        return jdbcClient.sql("""
            select distinct rider_id from trip_seat_locks
            where trip_id = :tripId and status = 'LOCKED' and rider_id is not null
            """)
            .param("tripId", tripId)
            .query(String.class)
            .list();
    }

    /** Marks the reminder fired; conditional so a concurrent scan can never double-mark. */
    boolean markDepartureReminderSent(String tripId, Instant now) {
        return jdbcClient.sql("""
            update trips
            set departure_reminder_sent_at = :now, updated_at = :now
            where trip_id = :tripId and departure_reminder_sent_at is null
            """)
            .param("now", Timestamp.from(now))
            .param("tripId", tripId)
            .update() > 0;
    }

    record DepartureReminderTrip(
        String tripId,
        String driverId,
        String originText,
        String destinationText,
        Instant departureAt
    ) {
    }

    private TripOffer requireTrip(String tripId) {
        return findByTripId(tripId).orElseThrow(() -> new IllegalArgumentException("trip not found: " + tripId));
    }

    private TripInventoryRow requireTripForUpdate(String tripId) {
        return jdbcClient.sql("""
            select trip_id, total_seats, locked_seats, status
            from trips
            where trip_id = :tripId
            for update
            """)
            .param("tripId", tripId)
            .query((resultSet, rowNumber) -> new TripInventoryRow(
                resultSet.getString("trip_id"),
                resultSet.getInt("total_seats"),
                resultSet.getInt("locked_seats"),
                resultSet.getString("status")
            ))
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("trip not found: " + tripId));
    }

    private Optional<SeatLock> findSeatLock(String orderId) {
        return jdbcClient.sql("""
            select trip_id, order_id, seats, status
            from trip_seat_locks
            where order_id = :orderId
            """)
            .param("orderId", orderId)
            .query((resultSet, rowNumber) -> new SeatLock(
                resultSet.getString("trip_id"),
                resultSet.getString("order_id"),
                resultSet.getInt("seats"),
                resultSet.getString("status")
            ))
            .optional();
    }

    private TripOffer mapTrip(ResultSet resultSet, int rowNumber) throws SQLException {
        String tripId = resultSet.getString("trip_id");
        RouteSnapshot route = new RouteSnapshot(
            resultSet.getString("route_id"),
            resultSet.getInt("distance_meters"),
            resultSet.getInt("duration_seconds"),
            resultSet.getString("route_provider"),
            resultSet.getString("route_polyline"),
            mapLocation(resultSet, "origin"),
            mapLocation(resultSet, "destination")
        );
        return new TripOffer(
            tripId,
            resultSet.getString("driver_id"),
            resultSet.getString("origin_text"),
            resultSet.getString("destination_text"),
            resultSet.getTimestamp("departure_at").toInstant(),
            route,
            new SeatInventory(tripId, resultSet.getInt("total_seats"), resultSet.getInt("locked_seats")),
            new Money(resultSet.getBigDecimal("seat_price_amount"), resultSet.getString("seat_price_currency")),
            TripStatus.valueOf(resultSet.getString("status")),
            mapBreakdown(resultSet, route),
            TripSource.valueOf(resultSet.getString("source"))
        );
    }

    /**
     * Rebuilds the fare breakdown from the pricing components stored at publish time, so it is
     * always consistent with the stored seat price. Pre-migration rows (no components) get null.
     */
    private PriceBreakdown mapBreakdown(ResultSet resultSet, RouteSnapshot route) throws SQLException {
        BigDecimal baseFare = resultSet.getBigDecimal("base_fare");
        BigDecimal includedKm = resultSet.getBigDecimal("included_km");
        BigDecimal perKmFare = resultSet.getBigDecimal("per_km_fare");
        if (baseFare == null || includedKm == null || perKmFare == null) {
            return null;
        }
        BigDecimal minFare = resultSet.getBigDecimal("min_fare");
        String currency = resultSet.getString("seat_price_currency");
        return new PricingPolicy(baseFare, includedKm, perKmFare,
            minFare == null ? BigDecimal.ZERO : minFare, currency).quoteBreakdown(route);
    }

    /**
     * Rebuilds a {@link LocationRef} from its columns, or null for trips published before
     * structured locations existed. Datum is read from the row rather than assumed.
     */
    private LocationRef mapLocation(ResultSet resultSet, String prefix) throws SQLException {
        BigDecimal latitude = resultSet.getBigDecimal(prefix + "_lat");
        BigDecimal longitude = resultSet.getBigDecimal(prefix + "_lng");
        String adcode = resultSet.getString(prefix + "_adcode");
        if (latitude == null || longitude == null || !StringUtils.hasText(adcode)) {
            return null;
        }
        String datum = resultSet.getString("coordinate_datum");
        String text = resultSet.getString(prefix + "_text");
        return new LocationRef(
            new GeoPoint(
                latitude.doubleValue(),
                longitude.doubleValue(),
                StringUtils.hasText(datum) ? CoordinateDatum.valueOf(datum) : CoordinateDatum.GCJ02),
            resultSet.getString("route_provider"),
            resultSet.getString(prefix + "_place_id"),
            resultSet.getString(prefix + "_city_code"),
            adcode,
            text,
            text,
            LocationSource.MANUAL,
            null,
            resultSet.getTimestamp("departure_at").toInstant()
        );
    }

    private String displayText(LocationRef location, String fallback) {
        return location != null ? location.displayName() : fallback;
    }

    private void validatePublish(PublishTripCommand command) {
        if (!StringUtils.hasText(command.driverId())) {
            throw new IllegalArgumentException("driverId is required");
        }
        if (!StringUtils.hasText(command.originText())) {
            throw new IllegalArgumentException("originText is required");
        }
        if (!StringUtils.hasText(command.destinationText())) {
            throw new IllegalArgumentException("destinationText is required");
        }
        if (command.departureAt() == null) {
            throw new IllegalArgumentException("departureAt is required");
        }
        if (command.totalSeats() <= 0) {
            throw new IllegalArgumentException("totalSeats must be positive");
        }
    }

    private void validateSeatMutation(String tripId, String orderId, int seats) {
        if (!StringUtils.hasText(tripId)) {
            throw new IllegalArgumentException("tripId is required");
        }
        if (!StringUtils.hasText(orderId)) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (seats <= 0) {
            throw new IllegalArgumentException("seats must be positive");
        }
    }

    private record TripInventoryRow(String tripId, int totalSeats, int lockedSeats, String status) {
    }

    private record SeatLock(String tripId, String orderId, int seats, String status) {
    }
}
