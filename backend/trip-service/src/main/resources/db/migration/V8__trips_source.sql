-- Demo virtual trips are labelled at the row level so the UI can badge them as demo data and a
-- cleanup job can expire only demo rows. USER is the default; existing rows are real trips.
ALTER TABLE trips
  ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'USER',
  ADD INDEX idx_trips_source (source, departure_at);
