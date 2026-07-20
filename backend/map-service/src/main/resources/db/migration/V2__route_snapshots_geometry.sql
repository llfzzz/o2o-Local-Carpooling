-- Route snapshots gain geometry and structured endpoints, so a client can draw the route and a
-- trip can be matched geographically instead of by substring. All columns are nullable: rows
-- written before this migration carry only text and a resolved "lng,lat" string.
alter table route_snapshots
  add column polyline longtext null comment 'encoded route geometry from the provider',
  add column coordinate_datum varchar(16) null comment 'datum of the stored coordinates (GCJ02)',
  add column origin_adcode varchar(16) null,
  add column destination_adcode varchar(16) null,
  add column origin_city_code varchar(16) null,
  add column destination_city_code varchar(16) null,
  add column origin_place_id varchar(64) null,
  add column destination_place_id varchar(64) null,
  add column cache_key varchar(128) null comment 'dedupe key for repeated identical quotes';

-- Full POI names and formatted addresses do not fit in 120 characters.
alter table route_snapshots
  modify column origin_text varchar(200) not null,
  modify column destination_text varchar(200) not null;

create index idx_route_snapshots_cache on route_snapshots (cache_key, created_at);
