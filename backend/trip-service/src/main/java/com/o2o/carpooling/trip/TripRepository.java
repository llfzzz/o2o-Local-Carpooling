package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.Money;
import com.o2o.carpooling.common.domain.PricingPolicy;
import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.domain.SeatInventory;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.domain.TripStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class TripRepository {

    private final JdbcClient jdbcClient;
    private final PricingPolicy pricingPolicy = new PricingPolicy(new BigDecimal("6.00"), new BigDecimal("1.20"));

    TripRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    TripOffer create(PublishTripCommand command, RouteSnapshot route) {
        validatePublish(command);
        if (route == null) {
            throw new IllegalArgumentException("route is required");
        }
        String tripId = "trip-" + UUID.randomUUID();
        Money price = pricingPolicy.quote(route);
        Instant now = Instant.now();

        jdbcClient.sql("""
            insert into trips (
              trip_id, driver_id, origin_text, destination_text, departure_at,
              route_id, distance_meters, duration_seconds, route_provider,
              seat_price_amount, seat_price_currency, total_seats, locked_seats,
              status, created_at, updated_at, version
            ) values (
              :tripId, :driverId, :originText, :destinationText, :departureAt,
              :routeId, :distanceMeters, :durationSeconds, :routeProvider,
              :seatPriceAmount, :seatPriceCurrency, :totalSeats, 0,
              :status, :createdAt, :updatedAt, 0
            )
            """)
            .param("tripId", tripId)
            .param("driverId", command.driverId())
            .param("originText", command.originText())
            .param("destinationText", command.destinationText())
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
                   seat_price_amount, seat_price_currency, total_seats, locked_seats, status
            from trips
            where trip_id = :tripId
            """)
            .param("tripId", tripId)
            .query(this::mapTrip)
            .optional();
    }

    List<TripOffer> search(String originText, String destinationText) {
        String origin = normalized(originText);
        String destination = normalized(destinationText);
        return jdbcClient.sql("""
            select trip_id, driver_id, origin_text, destination_text, departure_at,
                   route_id, distance_meters, duration_seconds, route_provider,
                   seat_price_amount, seat_price_currency, total_seats, locked_seats, status
            from trips
            where status = :status
              and (:originText is null or origin_text like :originLike)
              and (:destinationText is null or destination_text like :destinationLike)
            order by departure_at asc, id asc
            """)
            .param("status", TripStatus.PUBLISHED.name())
            .param("originText", origin)
            .param("originLike", like(origin))
            .param("destinationText", destination)
            .param("destinationLike", like(destination))
            .query(this::mapTrip)
            .list();
    }

    @Transactional
    TripOffer lockSeats(String tripId, String orderId, int seats) {
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
            insert into trip_seat_locks (trip_id, order_id, seats, status, created_at)
            values (:tripId, :orderId, :seats, 'LOCKED', :createdAt)
            """)
            .param("tripId", tripId)
            .param("orderId", orderId)
            .param("seats", seats)
            .param("createdAt", Instant.now())
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

    long countPublishedTrips() {
        return jdbcClient.sql("select count(*) from trips where status = :status")
            .param("status", TripStatus.PUBLISHED.name())
            .query(Long.class)
            .single();
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
            resultSet.getString("route_provider")
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
            TripStatus.valueOf(resultSet.getString("status"))
        );
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String like(String value) {
        return value == null ? "%" : "%" + value + "%";
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
