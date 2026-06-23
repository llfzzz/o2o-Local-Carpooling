CREATE TABLE IF NOT EXISTS payment_simulations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_id VARCHAR(64) NOT NULL UNIQUE,
  order_id VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  currency VARCHAR(8) NOT NULL,
  status VARCHAR(32) NOT NULL,
  paid_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_payment_sim_order_idempotency (order_id, idempotency_key),
  INDEX idx_payment_sim_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
