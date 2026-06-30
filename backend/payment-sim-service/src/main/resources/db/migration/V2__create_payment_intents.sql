CREATE TABLE IF NOT EXISTS payment_intents (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  intent_id VARCHAR(64) NOT NULL UNIQUE,
  order_id VARCHAR(64) NOT NULL,
  rider_id VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  currency VARCHAR(8) NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_ref VARCHAR(120),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_payment_intent_order_idem (order_id, idempotency_key),
  INDEX idx_payment_intent_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_callback_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(80) NOT NULL UNIQUE,
  intent_id VARCHAR(64) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  received_at TIMESTAMP(3) NOT NULL,
  INDEX idx_payment_callback_intent (intent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
