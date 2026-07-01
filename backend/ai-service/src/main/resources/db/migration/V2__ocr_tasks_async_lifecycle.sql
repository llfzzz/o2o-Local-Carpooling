-- Add async task lifecycle to ocr_tasks: a task is SUBMITTED/PROCESSING before it COMPLETES, so
-- result_json and completed_at are now nullable while in flight.
ALTER TABLE ocr_tasks
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
  ADD COLUMN provider_ref VARCHAR(120),
  ADD COLUMN submitted_at TIMESTAMP(3) NULL,
  MODIFY COLUMN result_json JSON NULL,
  MODIFY COLUMN completed_at TIMESTAMP(3) NULL;
