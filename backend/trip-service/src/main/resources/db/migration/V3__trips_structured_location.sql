-- Trips gain structured endpoints so matching can be geographic instead of a substring LIKE.
-- Plain DECIMAL columns rather than MySQL SPATIAL: repository tests run on H2, where ST_* is
-- unavailable, and a composite B-tree index on (status, departure_at, lat, lng) serves the
-- bounding-box pre-filter this needs just as well at the volumes involved.
alter table trips
  add column origin_lat decimal(10, 7) null,
  add column origin_lng decimal(10, 7) null,
  add column destination_lat decimal(10, 7) null,
  add column destination_lng decimal(10, 7) null,
  add column coordinate_datum varchar(16) null comment 'datum of the stored coordinates (GCJ02)',
  add column origin_adcode varchar(16) null,
  add column destination_adcode varchar(16) null,
  add column origin_city_code varchar(16) null,
  add column destination_city_code varchar(16) null,
  add column origin_place_id varchar(64) null,
  add column destination_place_id varchar(64) null,
  add column route_polyline longtext null;

-- Full POI names and formatted addresses do not fit in 120 characters.
alter table trips
  modify column origin_text varchar(200) not null,
  modify column destination_text varchar(200) not null;

-- Serves the bounding-box pre-filter: status and departure window narrow first, then latitude.
create index idx_trips_geo on trips (status, departure_at, origin_lat, origin_lng);
