-- Message Center promotion: deliveries can point at the business object they concern
-- (link_type + link_id drive the in-app deep link), and senders may pass a dedupe_key so
-- at-least-once outbox relays cannot create duplicate inbox rows.
ALTER TABLE notification_deliveries
  ADD COLUMN link_type VARCHAR(32) NULL,
  ADD COLUMN link_id VARCHAR(64) NULL,
  ADD COLUMN dedupe_key VARCHAR(96) NULL,
  ADD UNIQUE INDEX uk_notif_dedupe (dedupe_key),
  ADD INDEX idx_notif_user_read (user_id, read_at),
  ADD INDEX idx_notif_user_category (user_id, category, created_at);
