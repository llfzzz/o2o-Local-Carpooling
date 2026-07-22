-- The admin dashboard's today-order count filters `created_at >= :todayStart`, and the admin order
-- list orders by `created_at desc`. Neither could use the existing idx_orders_rider_created
-- (rider_id, created_at) index because its leftmost column is rider_id, so both full-scanned the
-- orders table on every dashboard load. A created_at index serves the count directly and provides
-- the sort order for the (now bounded) admin order list.
create index idx_orders_created on orders (created_at);
