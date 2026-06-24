CREATE TABLE IF NOT EXISTS route_snapshots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  route_id VARCHAR(64) NOT NULL UNIQUE,
  origin_text VARCHAR(120) NOT NULL,
  destination_text VARCHAR(120) NOT NULL,
  city VARCHAR(64),
  origin_coordinate VARCHAR(64),
  destination_coordinate VARCHAR(64),
  distance_meters INT NOT NULL,
  duration_seconds INT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_trace VARCHAR(64) NOT NULL,
  provider_response_snapshot LONGTEXT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_route_snapshots_origin_destination (origin_text, destination_text, created_at),
  INDEX idx_route_snapshots_provider (provider, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
