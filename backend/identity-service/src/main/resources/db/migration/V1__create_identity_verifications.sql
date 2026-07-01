CREATE TABLE IF NOT EXISTS identity_verifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  verification_id VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  status VARCHAR(32) NOT NULL,
  liveness_status VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_ref VARCHAR(120),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_identity_user_idem (user_id, idempotency_key),
  INDEX idx_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
