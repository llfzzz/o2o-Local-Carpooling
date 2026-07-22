-- Proximity search filters status (eq) + an origin lat/lng bounding box, with departure_at as an
-- optional time window. The existing idx_trips_geo (status, departure_at, origin_lat, origin_lng)
-- puts the latitude columns behind the departure_at range, so they cannot narrow the index seek,
-- and when no time window is supplied it degrades to scanning every PUBLISHED trip. This index leads
-- with the geo columns so status + origin latitude can seek. It is kept alongside idx_trips_geo
-- (not a replacement) until EXPLAIN confirms the optimizer prefers it; a geohash/SPATIAL column is
-- the longer-term fix and is intentionally deferred. Plain B-tree keeps H2 repository tests working.
create index idx_trips_geo_seek on trips (status, origin_lat, origin_lng, departure_at);
