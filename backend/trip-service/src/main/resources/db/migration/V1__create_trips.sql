CREATE TABLE IF NOT EXISTS trips (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trip_id VARCHAR(64) NOT NULL UNIQUE,
  driver_id VARCHAR(64) NOT NULL,
  origin_text VARCHAR(120) NOT NULL,
  destination_text VARCHAR(120) NOT NULL,
  departure_at TIMESTAMP(3) NOT NULL,
  route_id VARCHAR(64) NOT NULL,
  distance_meters INT NOT NULL,
  duration_seconds INT NOT NULL,
  route_provider VARCHAR(64) NOT NULL,
  seat_price_amount DECIMAL(12, 2) NOT NULL,
  seat_price_currency VARCHAR(8) NOT NULL,
  total_seats INT NOT NULL,
  locked_seats INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  INDEX idx_trips_search (status, origin_text, destination_text, departure_at),
  INDEX idx_trips_driver (driver_id, departure_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS trip_seat_locks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trip_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL UNIQUE,
  seats INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  released_at TIMESTAMP(3),
  INDEX idx_trip_seat_locks_trip (trip_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
