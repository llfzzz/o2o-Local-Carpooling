-- Departure reminders: a scheduled scan notifies the driver and every LOCKED-seat rider once
-- per trip shortly before departure_at. The marker column makes the scan idempotent; the
-- receiver-side dedupe key (tripId:userId:DEPARTURE) additionally absorbs partial-failure
-- retries, so a crash between notify and mark can never double-notify.
ALTER TABLE trips
  ADD COLUMN departure_reminder_sent_at TIMESTAMP(3) NULL,
  ADD INDEX idx_trips_departure_reminder (status, departure_at, departure_reminder_sent_at);
