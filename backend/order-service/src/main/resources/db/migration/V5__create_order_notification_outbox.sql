-- Service-local notification outbox: user-facing Message Center notices for order lifecycle
-- transitions are enqueued in the SAME transaction as the state change, then a relay delivers
-- them to notification-service with retry (at-least-once; the receiver dedupes on event_id).
-- Same pattern as order_audit_outbox; distinct from order_outbox_events (RabbitMQ timeout path).
CREATE TABLE IF NOT EXISTS order_notification_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64) NOT NULL,
  category VARCHAR(48) NOT NULL,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(512) NOT NULL,
  link_type VARCHAR(32),
  link_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(3) NOT NULL,
  last_error VARCHAR(512),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  sent_at TIMESTAMP(3),
  INDEX idx_order_notif_outbox_sendable (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
