CREATE TABLE IF NOT EXISTS order_reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  review_id VARCHAR(64) NOT NULL UNIQUE,
  order_id VARCHAR(64) NOT NULL UNIQUE,
  trip_id VARCHAR(64) NOT NULL,
  reviewer_id VARCHAR(64) NOT NULL,
  rating INT NOT NULL,
  comment VARCHAR(500),
  created_at TIMESTAMP(3) NOT NULL,
  INDEX idx_order_reviews_trip (trip_id),
  INDEX idx_order_reviews_reviewer (reviewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
