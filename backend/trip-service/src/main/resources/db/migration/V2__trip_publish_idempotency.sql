-- Trip publish was the only create-type write without an idempotency key, so a retried request
-- created a duplicate trip and burned a second provider route quote. Nullable so rows written
-- before this migration remain valid; the unique key ignores NULLs in MySQL.
alter table trips
  add column idempotency_key varchar(80) null comment 'per-driver idempotency key for publish';

create unique index uk_trips_driver_idempotency on trips (driver_id, idempotency_key);
