-- Passenger-driver chat. A conversation is bound to exactly one legitimate order (unique
-- order_id) and its participants are frozen server-side at creation from the order (rider) and
-- trip (driver) records — clients never supply user ids. Membership checks on the hot polling
-- path are plain column comparisons. Read cursors are per-participant message-id watermarks.
CREATE TABLE IF NOT EXISTS chat_conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id VARCHAR(64) NOT NULL UNIQUE,
  order_id VARCHAR(64) NOT NULL UNIQUE,
  trip_id VARCHAR(64) NOT NULL,
  driver_id VARCHAR(64) NOT NULL,
  rider_id VARCHAR(64) NOT NULL,
  origin_text VARCHAR(200) NOT NULL,
  destination_text VARCHAR(200) NOT NULL,
  status VARCHAR(16) NOT NULL,
  driver_last_read_id BIGINT NOT NULL DEFAULT 0,
  rider_last_read_id BIGINT NOT NULL DEFAULT 0,
  last_message_at TIMESTAMP(3) NULL,
  last_message_preview VARCHAR(120) NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_chat_conv_driver (driver_id, last_message_at),
  INDEX idx_chat_conv_rider (rider_id, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id VARCHAR(64) NOT NULL UNIQUE,
  conversation_id VARCHAR(64) NOT NULL,
  sender_id VARCHAR(64) NOT NULL,
  body VARCHAR(500) NOT NULL,
  client_msg_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_chat_msg_client (conversation_id, sender_id, client_msg_id),
  INDEX idx_chat_msg_conv (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
