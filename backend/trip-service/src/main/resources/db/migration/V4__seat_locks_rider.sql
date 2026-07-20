-- Seat locks record which rider holds them.
--
-- Needed because live driver location is visible only to riders who actually booked the trip, and
-- trip-service previously had no way to answer "did this user book this trip" without calling
-- order-service on every watch request. The value comes from order-service, which resolves it from
-- the authenticated principal; the seat-lock endpoint is internal-only, so it is not client input.
--
-- Nullable: locks created before this migration have no recorded rider and therefore grant no
-- location access, which is the safe direction to fail.
alter table trip_seat_locks
  add column rider_id varchar(64) null comment 'rider holding this lock; gates live-location access';

create index idx_trip_seat_locks_rider on trip_seat_locks (rider_id, status);
