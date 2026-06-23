CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL UNIQUE,
  phone VARBINARY(256) NOT NULL,
  roles_json JSON NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS driver_profiles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  driver_id VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_driver_profiles_user_id (user_id),
  INDEX idx_driver_profiles_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS vehicles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  vehicle_id VARCHAR(64) NOT NULL UNIQUE,
  driver_id VARCHAR(64) NOT NULL,
  plate_no VARBINARY(256) NOT NULL,
  model VARCHAR(120) NOT NULL,
  seats INT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_vehicles_driver_id (driver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS driver_verification_cases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_id VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  uploaded_file_ids_json JSON NOT NULL,
  ocr_result_json JSON NOT NULL,
  submitted_at TIMESTAMP(3) NOT NULL,
  reviewed_at TIMESTAMP(3) NULL,
  reviewer_id VARCHAR(64) NULL,
  review_reason VARCHAR(500) NULL,
  INDEX idx_driver_verification_user_id (user_id),
  INDEX idx_driver_verification_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS trips (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trip_id VARCHAR(64) NOT NULL UNIQUE,
  driver_id VARCHAR(64) NOT NULL,
  origin_text VARCHAR(200) NOT NULL,
  destination_text VARCHAR(200) NOT NULL,
  departure_at TIMESTAMP(3) NOT NULL,
  route_snapshot_json JSON NOT NULL,
  seat_price_cents BIGINT NOT NULL,
  currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
  total_seats INT NOT NULL,
  locked_seats INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_trips_search (origin_text, destination_text, departure_at),
  INDEX idx_trips_driver_id (driver_id),
  INDEX idx_trips_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id VARCHAR(64) NOT NULL UNIQUE,
  trip_id VARCHAR(64) NOT NULL,
  rider_id VARCHAR(64) NOT NULL,
  seats INT NOT NULL,
  amount_cents BIGINT NOT NULL,
  currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
  status VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  paid_at TIMESTAMP(3) NULL,
  cancelled_at TIMESTAMP(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_orders_idempotency (rider_id, idempotency_key),
  INDEX idx_orders_trip_id (trip_id),
  INDEX idx_orders_rider_id (rider_id),
  INDEX idx_orders_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_simulations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_id VARCHAR(64) NOT NULL UNIQUE,
  order_id VARCHAR(64) NOT NULL,
  amount_cents BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider_snapshot_json JSON NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_payment_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS file_objects (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id VARCHAR(64) NOT NULL UNIQUE,
  owner_user_id VARCHAR(64) NOT NULL,
  bucket VARCHAR(128) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  sha256 CHAR(64) NOT NULL,
  visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_file_owner (owner_user_id),
  INDEX idx_file_sha256 (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outbox_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  published_at TIMESTAMP(3) NULL,
  INDEX idx_outbox_status_created (status, created_at),
  INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
