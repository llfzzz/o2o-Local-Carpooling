CREATE TABLE IF NOT EXISTS notification_deliveries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  delivery_id VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  category VARCHAR(48) NOT NULL,
  title VARCHAR(160) NOT NULL,
  masked_preview VARCHAR(255) NOT NULL,
  revealable_payload VARCHAR(512),
  reveal_expires_at TIMESTAMP(3) NULL,
  status VARCHAR(16) NOT NULL,
  correlation_id VARCHAR(64),
  retry_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  read_at TIMESTAMP(3) NULL,
  INDEX idx_notif_user_created (user_id, created_at),
  INDEX idx_notif_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
