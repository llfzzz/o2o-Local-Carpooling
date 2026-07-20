-- The substring-LIKE search this index served is gone: matching is geographic now
-- (idx_trips_geo). A leading '%' made this index unusable for that query anyway, so it was only
-- ever costing write throughput.
drop index idx_trips_search on trips;
