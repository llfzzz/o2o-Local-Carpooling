-- Distance-based pricing: fare = max(minFare, base + max(0, km - includedKm) × perKm).
-- The policy components used at publish time are stored on the row so the displayed breakdown
-- always reconstructs the exact stored seat price, even after the pricing config changes.
-- Nullable: pre-migration rows have no components and render without a breakdown.
ALTER TABLE trips
  ADD COLUMN base_fare DECIMAL(10,2) NULL,
  ADD COLUMN included_km DECIMAL(7,3) NULL,
  ADD COLUMN per_km_fare DECIMAL(10,2) NULL,
  ADD COLUMN min_fare DECIMAL(10,2) NULL;
